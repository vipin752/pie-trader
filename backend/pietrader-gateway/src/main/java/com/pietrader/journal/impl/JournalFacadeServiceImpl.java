package com.pietrader.journal.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.journal.IJournalFacadeService;
import com.pietrader.journal.IJournalService;
import com.pietrader.journal.model.JournalEntry;
import com.pietrader.journal.model.JournalExitEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class JournalFacadeServiceImpl implements IJournalFacadeService {

    private final IJournalService journalService;

    @Override
    public void record(Trade trade, OptionAnalyticsDTO dto) {
        try {
            journalService.saveEntry(JournalEntry.builder()
                .tradeId(trade.getOrderId()).symbol(trade.getSymbol())
                .strike(trade.getStrike()).direction(trade.getDirection())
                .lots(trade.getLots()).entryPrice(trade.getEntryPrice())
                .sl(trade.getSl()).target(trade.getTarget())
                .confidence(trade.getConfidence()).regime(trade.getRegime())
                .strategy(trade.getStrategy()).tradeType(trade.getTradeType())
                .mode(trade.getMode() != null ? trade.getMode().name() : "PAPER")
                .gammaExposure(resolveGamma(dto)).ivContext(resolveIvContext(dto))
                .pcrValue(resolvePcr(dto)).fearIndex(resolveFearIndex(dto))
                .sessionPhase(resolveSessionPhase(dto)).decisionReason(resolveReason(dto))
                .rawDecisionJson(buildJson(dto)).recordedAt(Instant.now().toEpochMilli())
                .build());
            log.info("📓 Journal ENTRY → tradeId={} symbol={}", trade.getOrderId(), trade.getSymbol());
        } catch (Exception e) { log.error("❌ record failed {}: {}", trade.getSymbol(), e.getMessage(), e); }
    }

    @Override
    public void recordExit(String symbol, double exitPrice, String exitReason, double pnl) {
        try {
            journalService.saveExit(JournalExitEntry.builder()
                .symbol(symbol).exitPrice(exitPrice).exitReason(exitReason)
                .pnl(pnl).exitAt(Instant.now().toEpochMilli()).build());
            log.info("📓 Journal EXIT → {} reason={} pnl=₹{:.2f}", symbol, exitReason, pnl);
        } catch (Exception e) { log.error("❌ recordExit failed {}: {}", symbol, e.getMessage(), e); }
    }

    // ── all DTO paths verified against actual DTO classes ────────────────────
    private Double resolveGamma(OptionAnalyticsDTO dto)      { try { if(dto==null||dto.getDealerPositioning()==null||dto.getDealerPositioning().getGamma()==null) return null; return dto.getDealerPositioning().getGamma().getNetGamma(); } catch(Exception e){return null;} }
    private String resolveIvContext(OptionAnalyticsDTO dto)  { try { if(dto==null||dto.getDealerPositioning()==null||dto.getDealerPositioning().getDealerInventoryModel()==null) return null; return dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime(); } catch(Exception e){return null;} }
    private Double resolvePcr(OptionAnalyticsDTO dto)        { try { if(dto==null||dto.getVolatilityContext()==null||dto.getVolatilityContext().getPcr()==null) return null; return dto.getVolatilityContext().getPcr().getPcr(); } catch(Exception e){return null;} }
    private Double resolveFearIndex(OptionAnalyticsDTO dto)  { try { if(dto==null||dto.getCompleteDecision()==null||dto.getCompleteDecision().getFearIndexAnalysis()==null) return null; return dto.getCompleteDecision().getFearIndexAnalysis().getCurrentFearIndex(); } catch(Exception e){return null;} }
    private String resolveSessionPhase(OptionAnalyticsDTO dto){ try { if(dto==null||dto.getMarketContext()==null||dto.getMarketContext().getSession()==null) return null; return dto.getMarketContext().getSession().getSession(); } catch(Exception e){return null;} }
    private String resolveReason(OptionAnalyticsDTO dto)     { try { if(dto==null||dto.getAutoTradeDecision()==null||dto.getAutoTradeDecision().getAutoTradeAction()==null) return null; return dto.getAutoTradeDecision().getAutoTradeAction().getReason(); } catch(Exception e){return null;} }
    private String buildJson(OptionAnalyticsDTO dto)         { try { if(dto==null) return null; String sym=dto.getMarketContext()!=null?dto.getMarketContext().getSymbol():"?"; int c=dto.getConfidence()!=null&&dto.getConfidence().getConfidenceScore()!=null?dto.getConfidence().getConfidenceScore():0; String act=dto.getAutoTradeDecision()!=null&&dto.getAutoTradeDecision().getAutoTradeAction()!=null?dto.getAutoTradeDecision().getAutoTradeAction().getAction():"?"; String reg=dto.getHistoricalContext()!=null?dto.getHistoricalContext().getRegime():"UNKNOWN"; return String.format("{\"symbol\":\"%s\",\"confidence\":%d,\"action\":\"%s\",\"regime\":\"%s\"}",sym,c,act,reg!=null?reg:"UNKNOWN"); } catch(Exception e){return null;} }
}
