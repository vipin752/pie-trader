package com.pietrader.execution.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.IExitEngineService;
import com.pietrader.execution.IPositionManagerService;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.journal.IJournalFacadeService;
import com.pietrader.risk.IRiskService;
import com.pietrader.service.ISquareOffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExitEngineServiceImpl implements IExitEngineService {

    private final IPositionManagerService positionManager;
    private final ISquareOffService       squareOffService;
    private final IRiskService            riskManager;
    private final IJournalFacadeService   journalService;

    @Value("${trading.trailing.sl.activate.pct:20.0}") private double trailingActivatePct;
    @Value("${trading.trailing.sl.lock.pct:10.0}")     private double trailingLockPct;
    @Value("${trading.time.exit:15:10}")               private String timeExitStr;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Map<String, ExitRules>          rulesMap = new ConcurrentHashMap<>();
    private final Map<String, OptionAnalyticsDTO> ticks    = new ConcurrentHashMap<>();

    @Override
    public void register(Trade trade, OptionAnalyticsDTO dto) {
        if (trade == null || !trade.isSuccess()) return;
        rulesMap.put(trade.getSymbol(), ExitRules.builder()
            .symbol(trade.getSymbol()).strike(trade.getStrike())
            .direction(trade.getDirection()).entryPrice(trade.getEntryPrice())
            .sl(trade.getSl()).target(trade.getTarget()).trailingSl(trade.getSl())
            .lots(trade.getLots()).quantity(trade.getQuantity())
            .mode(trade.getMode()).entryTime(trade.getEntryTime())
            .trailingActive(false).build());
        log.info("🎯 ExitEngine registered → {} sl={} target={}",
            trade.getSymbol(), trade.getSl(), trade.getTarget());
    }

    @Override
    public void onAnalyticsTick(String symbol, OptionAnalyticsDTO dto) {
        if (symbol != null && dto != null) ticks.put(symbol, dto);
    }

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

    private String evaluate(String symbol, ExitRules rules, double ltp) {
        if (ltp <= rules.getSl())                                      return "STOP_LOSS";
        if (rules.getTarget() > 0 && ltp >= rules.getTarget())         return "TARGET_HIT";
        if (rules.isTrailingActive() && ltp <= rules.getTrailingSl())  return "TRAILING_SL";
        if (!LocalTime.now(IST).isBefore(LocalTime.parse(timeExitStr))) return "TIME_EXIT_1510";
        if (isGammaFlip(symbol))                                        return "GAMMA_FLIP";
        String opp = checkOppositeSignal(symbol, rules);
        if (opp != null)                                                return "OPPOSITE_SIGNAL_" + opp;
        if (isCompression(symbol) && isInProfit(rules, ltp))            return "COMPRESSION_QUICK_PROFIT";
        if (isPositiveGamma(symbol) && isInProfit(rules, ltp))          return "POSITIVE_GAMMA_QUICK_EXIT";
        if (isHighIv(symbol) && isInProfit(rules, ltp))                 return "HIGH_IV_FAST_EXIT";
        return null;
    }

    private void applyTrailing(String symbol, ExitRules rules, double ltp) {
        if (rules.getEntryPrice() <= 0) return;
        double gainPct   = ((ltp - rules.getEntryPrice()) / rules.getEntryPrice()) * 100.0;
        double activePct = (isLowIv(symbol) || isNegativeGamma(symbol)) ? trailingActivatePct * 1.5 : trailingActivatePct;
        double lockPct   = isExpansion(symbol) ? trailingLockPct * 0.7 : trailingLockPct;
        if (!rules.isTrailingActive() && gainPct >= activePct) {
            rules.setTrailingActive(true);
            log.info("🔼 [{}] Trailing activated gain={:.1f}%", symbol, gainPct);
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
            double pnl = "SELL".equalsIgnoreCase(rules.getDirection())
                ? (rules.getEntryPrice() - ltp) * rules.getQuantity()
                : (ltp - rules.getEntryPrice()) * rules.getQuantity();
            riskManager.onTradeClosed(symbol, pnl);
            journalService.recordExit(symbol, ltp, reason, pnl);
            rulesMap.remove(symbol);
            ticks.remove(symbol);
        } catch (Exception e) { log.error("❌ doExit failed {}: {}", symbol, e.getMessage(), e); }
    }

    // ── Detectors — all DTO paths verified ───────────────────────────────────
    private boolean isGammaFlip(String s)    { try { var d=ticks.get(s); if(d==null||d.getDealerPositioning()==null||d.getDealerPositioning().getDealerInventoryModel()==null) return false; Double f=d.getDealerPositioning().getDealerInventoryModel().getGammaFlip(); return f!=null&&f>0; } catch(Exception e){return false;} }
    private boolean isPositiveGamma(String s){ try { var d=ticks.get(s); if(d==null||d.getDealerPositioning()==null||d.getDealerPositioning().getGamma()==null) return false; Double g=d.getDealerPositioning().getGamma().getNetGamma(); return g!=null&&g>0; } catch(Exception e){return false;} }
    private boolean isNegativeGamma(String s){ try { var d=ticks.get(s); if(d==null||d.getDealerPositioning()==null||d.getDealerPositioning().getGamma()==null) return false; Double g=d.getDealerPositioning().getGamma().getNetGamma(); return g!=null&&g<0; } catch(Exception e){return false;} }
    private boolean isCompression(String s)  { try { var d=ticks.get(s); if(d==null||d.getMarketStructure()==null||d.getMarketStructure().getCompression()==null) return false; return Boolean.TRUE.equals(d.getMarketStructure().getCompression().getCompressionDetected()); } catch(Exception e){return false;} }
    private String  getVolRegime(String s)   { try { var d=ticks.get(s); if(d==null||d.getDealerPositioning()==null||d.getDealerPositioning().getDealerInventoryModel()==null) return ""; String v=d.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime(); return v!=null?v:""; } catch(Exception e){return "";} }
    private boolean isExpansion(String s)    { String v=getVolRegime(s); return "EXPANDING".equalsIgnoreCase(v)||"HIGH_IV".equalsIgnoreCase(v); }
    private boolean isHighIv(String s)       { return "HIGH_IV".equalsIgnoreCase(getVolRegime(s)); }
    private boolean isLowIv(String s)        { String v=getVolRegime(s); return "LOW_IV".equalsIgnoreCase(v)||"CONTRACTING".equalsIgnoreCase(v); }
    private boolean isInProfit(ExitRules r, double ltp) { return "SELL".equalsIgnoreCase(r.getDirection()) ? ltp<r.getEntryPrice() : ltp>r.getEntryPrice(); }
    private double  resolveLtp(String s)     { try { var d=ticks.get(s); if(d!=null&&d.getMarketContext()!=null){Double sp=d.getMarketContext().getSpot(); if(sp!=null)return sp;} } catch(Exception e){} return 0.0; }
    private String  checkOppositeSignal(String s, ExitRules r) { try { var d=ticks.get(s); if(d==null||d.getAutoTradeDecision()==null||d.getAutoTradeDecision().getAutoTradeAction()==null) return null; String nd=d.getAutoTradeDecision().getAutoTradeAction().getDirection(); int c=d.getConfidence()!=null&&d.getConfidence().getConfidenceScore()!=null?d.getConfidence().getConfidenceScore():0; if(c>=75&&nd!=null&&!nd.equalsIgnoreCase(r.getDirection())) return nd; } catch(Exception e){} return null; }

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
