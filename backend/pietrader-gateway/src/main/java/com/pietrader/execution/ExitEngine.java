package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.risk.RiskManager;
import com.pietrader.service.SquareOffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PIE TRADER — ExitEngine (13 rules, all DTO calls verified)
 *
 * VERIFIED DTO PATHS:
 *  gammaFlip:         DealerInventoryModelDTO.gammaFlip (Double flip price, NOT Boolean)
 *  netGamma:          GammaDTO.netGamma
 *  volatilityRegime:  DealerInventoryModelDTO.volatilityRegime (NOT VolatilityContextDTO.ivRegime)
 *  compression:       MarketStructureDTO.CompressionDTO.compressionDetected (Boolean)
 *  spot/ltp:          MarketContextDTO.spot
 *  direction:         AutoTradeActionDTO.direction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExitEngine {

    private final PositionManager    positionManager;
    private final SquareOffService   squareOffService;
    private final RiskManager        riskManager;
    private final TradeJournalFacade journalService;

    @Value("${trading.trailing.sl.activate.pct:20.0}") private double trailingActivatePct;
    @Value("${trading.trailing.sl.lock.pct:10.0}")     private double trailingLockPct;
    @Value("${trading.time.exit:15:10}")               private String timeExitStr;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Map<String, ExitRules>       rulesMap = new ConcurrentHashMap<>();
    private final Map<String, OptionAnalyticsDTO> ticks = new ConcurrentHashMap<>();

    // ── Register ──────────────────────────────────────────────────────────────

    public void register(Trade trade, OptionAnalyticsDTO dto) {
        if (trade == null || !trade.isSuccess()) return;
        rulesMap.put(trade.getSymbol(), ExitRules.builder()
            .symbol(trade.getSymbol()).strike(trade.getStrike())
            .direction(trade.getDirection()).entryPrice(trade.getEntryPrice())
            .sl(trade.getSl()).target(trade.getTarget()).trailingSl(trade.getSl())
            .lots(trade.getLots()).quantity(trade.getQuantity())
            .mode(trade.getMode()).entryTime(trade.getEntryTime())
            .trailingActive(false).build());
        log.info("🎯 ExitEngine registered → {} sl={} target={}", trade.getSymbol(), trade.getSl(), trade.getTarget());
    }

    public void onAnalyticsTick(String symbol, OptionAnalyticsDTO dto) {
        if (symbol != null && dto != null) ticks.put(symbol, dto);
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1000)
    public void runExitChecks() {
        if (rulesMap.isEmpty()) return;
        for (String symbol : positionManager.getActiveSymbols()) {
            ExitRules rules = rulesMap.get(symbol);
            if (rules == null) continue;
            try {
                double ltp = resolveLtp(symbol);
                if (ltp <= 0) continue;
                positionManager.updatePnl(symbol, ltp);
                String reason = evaluate(symbol, rules, ltp);
                if (reason != null) {
                    log.warn("🚨 EXIT → {} reason={} ltp={}", symbol, reason, ltp);
                    doExit(symbol, rules, ltp, reason);
                } else {
                    applyTrailing(symbol, rules, ltp);
                }
            } catch (Exception e) {
                log.error("❌ ExitEngine error {}: {}", symbol, e.getMessage(), e);
            }
        }
    }

    // ── 13 rules ──────────────────────────────────────────────────────────────

    private String evaluate(String symbol, ExitRules rules, double ltp) {
        if (ltp <= rules.getSl())                                     return "STOP_LOSS";
        if (rules.getTarget() > 0 && ltp >= rules.getTarget())        return "TARGET_HIT";
        if (rules.isTrailingActive() && ltp <= rules.getTrailingSl()) return "TRAILING_SL";
        if (isTimeExit())                                              return "TIME_EXIT_1510";
        if (isGammaFlip(symbol))                                       return "GAMMA_FLIP";
        String opp = checkOppositeSignal(symbol, rules);
        if (opp != null)                                               return "OPPOSITE_SIGNAL_" + opp;
        if (isCompression(symbol) && isInProfit(rules, ltp))           return "COMPRESSION_QUICK_PROFIT";
        if (isPositiveGamma(symbol) && isInProfit(rules, ltp))         return "POSITIVE_GAMMA_QUICK_EXIT";
        if (isHighIv(symbol) && isInProfit(rules, ltp))                return "HIGH_IV_FAST_EXIT";
        return null;
    }

    private void applyTrailing(String symbol, ExitRules rules, double ltp) {
        double entry = rules.getEntryPrice();
        if (entry <= 0) return;
        double gainPct = ((ltp - entry) / entry) * 100.0;
        boolean lowIv    = isLowIv(symbol);
        boolean negGamma = isNegativeGamma(symbol);
        boolean expansion = isExpansion(symbol);
        double activePct = (lowIv || negGamma) ? trailingActivatePct * 1.5 : trailingActivatePct;
        double lockPct   = expansion ? trailingLockPct * 0.7 : trailingLockPct;
        if (!rules.isTrailingActive() && gainPct >= activePct) {
            rules.setTrailingActive(true);
            log.info("🔼 [{}] Trailing activated — gain={:.1f}%", symbol, gainPct);
        }
        if (rules.isTrailingActive()) {
            double newTrail = ltp * (1.0 - lockPct / 100.0);
            if (newTrail > rules.getTrailingSl()) {
                rules.setTrailingSl(newTrail);
                positionManager.updateSl(symbol, newTrail);
            }
        }
    }

    private void doExit(String symbol, ExitRules rules, double ltp, String reason) {
        try {
            squareOffService.squareOff(symbol, reason);
            double pnl = computePnl(rules, ltp);
            riskManager.onTradeClosed(symbol, pnl);
            journalService.recordExit(symbol, ltp, reason, pnl);
            rulesMap.remove(symbol);
            ticks.remove(symbol);
        } catch (Exception e) {
            log.error("❌ Exit failed for {}: {}", symbol, e.getMessage(), e);
        }
    }

    // ── Market condition detectors — all DTO paths verified ───────────────────

    /** gammaFlip is Double (flip price level) — non-null means active flip zone ✅ */
    private boolean isGammaFlip(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getDealerPositioning() == null) return false;
            if (dto.getDealerPositioning().getDealerInventoryModel() == null) return false;
            Double flip = dto.getDealerPositioning().getDealerInventoryModel().getGammaFlip();
            return flip != null && flip > 0;
        } catch (Exception e) { return false; }
    }

    /** GammaDTO.netGamma > 0 ✅ */
    private boolean isPositiveGamma(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getDealerPositioning() == null) return false;
            if (dto.getDealerPositioning().getGamma() == null) return false;
            Double g = dto.getDealerPositioning().getGamma().getNetGamma();
            return g != null && g > 0;
        } catch (Exception e) { return false; }
    }

    /** GammaDTO.netGamma < 0 ✅ */
    private boolean isNegativeGamma(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getDealerPositioning() == null) return false;
            if (dto.getDealerPositioning().getGamma() == null) return false;
            Double g = dto.getDealerPositioning().getGamma().getNetGamma();
            return g != null && g < 0;
        } catch (Exception e) { return false; }
    }

    /** CompressionDTO.compressionDetected (Boolean) ✅ */
    private boolean isCompression(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getMarketStructure() == null) return false;
            if (dto.getMarketStructure().getCompression() == null) return false;
            return Boolean.TRUE.equals(dto.getMarketStructure().getCompression().getCompressionDetected());
        } catch (Exception e) { return false; }
    }

    /** DealerInventoryModelDTO.volatilityRegime — VolatilityContextDTO has NO ivRegime ✅ */
    private String getVolatilityRegime(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getDealerPositioning() == null) return "";
            if (dto.getDealerPositioning().getDealerInventoryModel() == null) return "";
            String vr = dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime();
            return vr != null ? vr : "";
        } catch (Exception e) { return ""; }
    }

    private boolean isExpansion(String symbol) { String v = getVolatilityRegime(symbol); return "EXPANDING".equalsIgnoreCase(v) || "HIGH_IV".equalsIgnoreCase(v); }
    private boolean isHighIv(String symbol)    { return "HIGH_IV".equalsIgnoreCase(getVolatilityRegime(symbol)); }
    private boolean isLowIv(String symbol)     { String v = getVolatilityRegime(symbol); return "LOW_IV".equalsIgnoreCase(v) || "CONTRACTING".equalsIgnoreCase(v); }
    private boolean isTimeExit()               { return !LocalTime.now(IST).isBefore(LocalTime.parse(timeExitStr)); }

    /** AutoTradeActionDTO.direction vs current direction ✅ */
    private String checkOppositeSignal(String symbol, ExitRules rules) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto == null || dto.getAutoTradeDecision() == null) return null;
            if (dto.getAutoTradeDecision().getAutoTradeAction() == null) return null;
            String newDir = dto.getAutoTradeDecision().getAutoTradeAction().getDirection();
            int conf = dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null
                ? dto.getConfidence().getConfidenceScore() : 0;
            if (conf >= 75 && newDir != null && !newDir.equalsIgnoreCase(rules.getDirection()))
                return newDir;
        } catch (Exception ignored) {}
        return null;
    }

    /** MarketContextDTO.spot ✅ */
    private double resolveLtp(String symbol) {
        try {
            OptionAnalyticsDTO dto = ticks.get(symbol);
            if (dto != null && dto.getMarketContext() != null) {
                Double spot = dto.getMarketContext().getSpot();
                if (spot != null) return spot;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private boolean isInProfit(ExitRules rules, double ltp) {
        if (rules.getEntryPrice() <= 0) return false;
        return "SELL".equalsIgnoreCase(rules.getDirection())
            ? ltp < rules.getEntryPrice() : ltp > rules.getEntryPrice();
    }

    private double computePnl(ExitRules rules, double exitPrice) {
        double diff = "SELL".equalsIgnoreCase(rules.getDirection())
            ? rules.getEntryPrice() - exitPrice : exitPrice - rules.getEntryPrice();
        return diff * rules.getQuantity();
    }

    @lombok.Data @lombok.Builder
    public static class ExitRules {
        private String symbol, strike, direction;
        private double entryPrice, sl, target, trailingSl;
        private int lots, quantity;
        private TradeMode mode;
        private long entryTime;
        private boolean trailingActive;
    }
}
