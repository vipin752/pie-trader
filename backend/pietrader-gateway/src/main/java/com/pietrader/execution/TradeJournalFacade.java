package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.journal.TradeJournalService;
import com.pietrader.journal.model.JournalEntry;
import com.pietrader.journal.model.JournalExitEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * PIE TRADER — TradeJournalFacade
 *
 * ALL DTO method calls verified against actual DTO field definitions:
 *
 *  WRONG → RIGHT:
 *  MarketContextDTO.getRegime()          → HistoricalContextDTO.getRegime()
 *  VolatilityContextDTO.getIvRegime()    → DealerInventoryModelDTO.getVolatilityRegime()
 *  SessionDTO.getSessionPhase()          → SessionDTO.getSession()
 *  PcrDTO.getPcrValue()                  → PcrDTO.getPcr()
 *  DealerInventoryModelDTO.getGammaFlip() → Double (flip price level, not Boolean)
 *  TradingCardDTO.getRationale()         → AutoTradeActionDTO.getReason()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeJournalFacade {

    private final TradeJournalService journalService;

    public void record(Trade trade, OptionAnalyticsDTO dto) {
        try {
            JournalEntry entry = JournalEntry.builder()
                .tradeId(trade.getOrderId())
                .symbol(trade.getSymbol())
                .strike(trade.getStrike())
                .direction(trade.getDirection())
                .lots(trade.getLots())
                .entryPrice(trade.getEntryPrice())
                .sl(trade.getSl())
                .target(trade.getTarget())
                .confidence(trade.getConfidence())
                .regime(trade.getRegime())
                .strategy(trade.getStrategy())
                .tradeType(trade.getTradeType())
                .mode(trade.getMode() != null ? trade.getMode().name() : "PAPER")
                .gammaExposure(resolveGamma(dto))
                .ivContext(resolveIvContext(dto))
                .pcrValue(resolvePcr(dto))
                .fearIndex(resolveFearIndex(dto))
                .sessionPhase(resolveSessionPhase(dto))
                .decisionReason(resolveDecisionReason(dto))
                .rawDecisionJson(buildCompactJson(dto))
                .recordedAt(Instant.now().toEpochMilli())
                .build();
            journalService.saveEntry(entry);
            log.info("📓 Journal ENTRY → tradeId={} symbol={}", trade.getOrderId(), trade.getSymbol());
        } catch (Exception e) {
            log.error("❌ Journal entry failed for {}: {}", trade.getSymbol(), e.getMessage(), e);
        }
    }

    public void recordExit(String symbol, double exitPrice, String exitReason, double pnl) {
        try {
            journalService.saveExit(JournalExitEntry.builder()
                .symbol(symbol).exitPrice(exitPrice)
                .exitReason(exitReason).pnl(pnl)
                .exitAt(Instant.now().toEpochMilli()).build());
            log.info("📓 Journal EXIT → {} reason={} pnl=₹{:.2f}", symbol, exitReason, pnl);
        } catch (Exception e) {
            log.error("❌ Journal exit failed for {}: {}", symbol, e.getMessage(), e);
        }
    }

    // ── DTO field extractors — all verified ───────────────────────────────────

    /** GammaDTO.netGamma ✅ */
    private Double resolveGamma(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getDealerPositioning() == null) return null;
            if (dto.getDealerPositioning().getGamma() == null) return null;
            return dto.getDealerPositioning().getGamma().getNetGamma();
        } catch (Exception e) { return null; }
    }

    /** DealerInventoryModelDTO.volatilityRegime — VolatilityContextDTO has NO ivRegime ✅ */
    private String resolveIvContext(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getDealerPositioning() == null) return null;
            if (dto.getDealerPositioning().getDealerInventoryModel() == null) return null;
            return dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime();
        } catch (Exception e) { return null; }
    }

    /** PcrDTO.pcr (Double) — field is .pcr NOT .pcrValue ✅ */
    private Double resolvePcr(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getVolatilityContext() == null) return null;
            if (dto.getVolatilityContext().getPcr() == null) return null;
            return dto.getVolatilityContext().getPcr().getPcr();
        } catch (Exception e) { return null; }
    }

    /** FearIndexAnalysisDTO.currentFearIndex ✅ */
    private Double resolveFearIndex(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getCompleteDecision() == null) return null;
            if (dto.getCompleteDecision().getFearIndexAnalysis() == null) return null;
            return dto.getCompleteDecision().getFearIndexAnalysis().getCurrentFearIndex();
        } catch (Exception e) { return null; }
    }

    /** SessionDTO.session (String) — NOT .sessionPhase ✅ */
    private String resolveSessionPhase(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getMarketContext() == null) return null;
            if (dto.getMarketContext().getSession() == null) return null;
            return dto.getMarketContext().getSession().getSession();
        } catch (Exception e) { return null; }
    }

    /** AutoTradeActionDTO.reason — TradingCardDTO has no rationale ✅ */
    private String resolveDecisionReason(OptionAnalyticsDTO dto) {
        try {
            if (dto == null || dto.getAutoTradeDecision() == null) return null;
            if (dto.getAutoTradeDecision().getAutoTradeAction() == null) return null;
            return dto.getAutoTradeDecision().getAutoTradeAction().getReason();
        } catch (Exception e) { return null; }
    }

    private String buildCompactJson(OptionAnalyticsDTO dto) {
        try {
            if (dto == null) return null;
            String symbol = dto.getMarketContext() != null ? dto.getMarketContext().getSymbol() : "?";
            int conf = dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null
                ? dto.getConfidence().getConfidenceScore() : 0;
            String action = dto.getAutoTradeDecision() != null
                && dto.getAutoTradeDecision().getAutoTradeAction() != null
                ? dto.getAutoTradeDecision().getAutoTradeAction().getAction() : "?";
            String regime = dto.getHistoricalContext() != null
                ? dto.getHistoricalContext().getRegime() : "UNKNOWN";
            return String.format("{\"symbol\":\"%s\",\"confidence\":%d,\"action\":\"%s\",\"regime\":\"%s\"}",
                symbol, conf, action, regime != null ? regime : "UNKNOWN");
        } catch (Exception e) { return null; }
    }
}
