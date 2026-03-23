package com.pietrader.execution.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.execution.IExecutionGateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
@Slf4j
public class ExecutionGateServiceImpl implements IExecutionGateService {

    @Value("${trading.min.confidence:65}")  private int    minConfidence;
    @Value("${trading.market.open:09:20}")  private String marketOpenStr;
    @Value("${trading.market.close:15:20}") private String marketCloseStr;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Override
    public GateResult check(OptionAnalyticsDTO dto) {
        if (dto == null) return GateResult.block("DTO is null");
        if (!isExecutionReady(dto)) return GateResult.block("execution_ready=false");
        String es = dto.getExecutionTiming() != null ? dto.getExecutionTiming().getEntrySignal() : null;
        if (es == null || "NO_ENTRY".equalsIgnoreCase(es)) return GateResult.block("entry_signal=" + es);
        AutoTradeActionDTO a = dto.getAutoTradeDecision() != null ? dto.getAutoTradeDecision().getAutoTradeAction() : null;
        String action = a != null ? a.getAction() : "UNKNOWN";
        if (!"EXECUTE".equalsIgnoreCase(action)) return GateResult.block("action=" + action);
        if (!isMarketOpen(dto)) return GateResult.block("Market closed");
        int conf = dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null ? dto.getConfidence().getConfidenceScore() : 0;
        if (conf < minConfidence) return GateResult.block("Confidence " + conf + " < " + minConfidence);
        return GateResult.allow();
    }

    private boolean isExecutionReady(OptionAnalyticsDTO dto) {
        return dto.getExecutionLayer() != null && dto.getExecutionLayer().getFinalExecution() != null && Boolean.TRUE.equals(dto.getExecutionLayer().getFinalExecution().getExecutionReady());
    }
    private boolean isMarketOpen(OptionAnalyticsDTO dto) {
        if (dto.getMarketContext() != null && dto.getMarketContext().getSession() != null) { Boolean m = dto.getMarketContext().getSession().getMarketOpen(); if (m != null) return m; }
        LocalTime now = LocalTime.now(IST);
        return !now.isBefore(LocalTime.parse(marketOpenStr)) && !now.isAfter(LocalTime.parse(marketCloseStr));
    }
}
