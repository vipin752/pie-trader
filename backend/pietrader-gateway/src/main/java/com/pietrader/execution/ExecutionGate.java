package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * PIE TRADER — ExecutionGate
 * All DTO method calls verified against actual DTO field definitions.
 */
@Component
@Slf4j
public class ExecutionGate {

    @Value("${trading.min.confidence:65}") private int    minConfidence;
    @Value("${trading.market.open:09:20}") private String marketOpenStr;
    @Value("${trading.market.close:15:20}") private String marketCloseStr;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public GateResult check(OptionAnalyticsDTO dto) {
        if (dto == null) return GateResult.block("DTO is null");

        // Gate 1: execution_ready — FinalExecutionDTO.executionReady (Boolean) ✅
        if (!isExecutionReady(dto))
            return GateResult.block("execution_ready=false → " + blockReason(dto));

        // Gate 2: entry_signal — ExecutionTimingDTO.entrySignal ✅
        String es = entrySignal(dto);
        if (es == null || "NO_ENTRY".equalsIgnoreCase(es))
            return GateResult.block("entry_signal=" + es);

        // Gate 3: action == EXECUTE — AutoTradeActionDTO.action ✅
        String action = resolveAction(dto);
        if (!"EXECUTE".equalsIgnoreCase(action))
            return GateResult.block("action=" + action + " (need EXECUTE)");

        // Gate 4: market open — SessionDTO.isMarket ✅
        if (!isMarketOpen(dto))
            return GateResult.block("Market session closed");

        // Gate 5: confidence — ConfidenceDTO.confidenceScore ✅
        int conf = resolveConfidence(dto);
        if (conf < minConfidence)
            return GateResult.block("Confidence " + conf + " < " + minConfidence);

        return GateResult.allow();
    }

    private boolean isExecutionReady(OptionAnalyticsDTO dto) {
        return dto.getExecutionLayer() != null
            && dto.getExecutionLayer().getFinalExecution() != null
            && Boolean.TRUE.equals(dto.getExecutionLayer().getFinalExecution().getExecutionReady());
    }
    private String blockReason(OptionAnalyticsDTO dto) {
        if (dto.getExecutionLayer() == null) return "no execution_layer";
        if (dto.getExecutionLayer().getFinalExecution() == null) return "no final_execution";
        String r = dto.getExecutionLayer().getFinalExecution().getReason();
        return r != null ? r : "unknown";
    }
    private String entrySignal(OptionAnalyticsDTO dto) {
        return dto.getExecutionTiming() != null ? dto.getExecutionTiming().getEntrySignal() : null;
    }
    private String resolveAction(OptionAnalyticsDTO dto) {
        AutoTradeActionDTO a = dto.getAutoTradeDecision() != null
            ? dto.getAutoTradeDecision().getAutoTradeAction() : null;
        return a != null ? a.getAction() : "UNKNOWN";
    }
    private boolean isMarketOpen(OptionAnalyticsDTO dto) {
        if (dto.getMarketContext() != null && dto.getMarketContext().getSession() != null) {
            Boolean m = dto.getMarketContext().getSession().getMarketOpen();
            if (m != null) return m;
        }
        LocalTime now = LocalTime.now(IST);
        return !now.isBefore(LocalTime.parse(marketOpenStr))
            && !now.isAfter(LocalTime.parse(marketCloseStr));
    }
    private int resolveConfidence(OptionAnalyticsDTO dto) {
        return dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null
            ? dto.getConfidence().getConfidenceScore() : 0;
    }

    @Getter
    public static class GateResult {
        private final boolean allowed;
        private final String  reason;
        private GateResult(boolean a, String r) { this.allowed = a; this.reason = r; }
        public static GateResult allow()              { return new GateResult(true,  "OK"); }
        public static GateResult block(String reason) { return new GateResult(false, reason); }
    }
}
