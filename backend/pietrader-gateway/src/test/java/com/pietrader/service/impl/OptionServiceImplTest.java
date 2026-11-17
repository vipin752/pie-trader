package com.pietrader.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.client.PythonAnalyticsClient;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.ExecutionService;
import com.pietrader.service.TradeSignalService;
import com.pietrader.state.TradeStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — OptionServiceImplTest (CR-002 regression)
 *
 * This is the exact defect the master security/architecture prompt's
 * CR-002 described: a GET-reachable method
 * (getOptionSummary -> /api/trade-card's cache-miss fallback,
 * /api/signals/live/{symbol}, and the old /api/options/summary) called
 * ExecutionService.execute() unconditionally — so a dashboard refresh or
 * a cache miss could silently place a (paper) order. ExecutionService.
 * execute()'s own javadoc says "called by Kafka consumer"; it was never
 * meant to be reachable from a read path.
 *
 * Fix: getOptionSummary() is now a pure read (fetch + cache only).
 * triggerAnalyticsCycle() is the one explicit command path that still
 * does fetch + persist + execute, used only by the new
 * POST /api/options/trigger endpoint.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OptionServiceImpl — CR-002: GET must never trigger execution")
class OptionServiceImplTest {

    @Mock PythonAnalyticsClient client;
    @Mock TradeSignalService    tradeSignalService;
    @Mock ExecutionService      executionService;
    @Mock TradeStateManager     stateManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OptionServiceImpl service;

    private static final String SYMBOL = "NIFTY";
    // A minimal but valid OptionAnalyticsDTO JSON — just enough for
    // deserialization to succeed in triggerAnalyticsCycle().
    private static final String ANALYTICS_JSON = "{\"marketContext\":{\"symbol\":\"NIFTY\",\"spot\":24500.0}}";

    @BeforeEach
    void setUp() {
        service = new OptionServiceImpl(client, tradeSignalService, executionService, stateManager, objectMapper);
    }

    @Test
    @DisplayName("getOptionSummary: never calls ExecutionService, even repeatedly")
    void getOptionSummary_neverTriggersExecution() {
        when(client.getOptionSummary(SYMBOL)).thenReturn(ANALYTICS_JSON);

        // Simulate repeated dashboard polling.
        service.getOptionSummary(SYMBOL);
        service.getOptionSummary(SYMBOL);
        service.getOptionSummary(SYMBOL);

        verifyNoInteractions(executionService);
        verifyNoInteractions(tradeSignalService);
    }

    @Test
    @DisplayName("getOptionSummary: a cache miss (fresh fetch) still never places an order")
    void getOptionSummary_cacheMissNeverPlacesOrder() {
        // "Cache miss" here = Python has to be hit fresh; stateManager has
        // no active-position state checked at all in the read path -
        // confirming it's gone, not just stubbed to pass.
        when(client.getOptionSummary(SYMBOL)).thenReturn(ANALYTICS_JSON);

        String result = service.getOptionSummary(SYMBOL);

        assertThat(result).isEqualTo(ANALYTICS_JSON);
        verify(stateManager).cacheSignal(SYMBOL, ANALYTICS_JSON);
        verify(stateManager, never()).hasActivePosition(anyString());
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("getOptionSummary: empty response from Python is reported, not executed on")
    void getOptionSummary_emptyResponse_noExecution() {
        when(client.getOptionSummary(SYMBOL)).thenReturn("");

        String result = service.getOptionSummary(SYMBOL);

        assertThat(result).contains("EMPTY_RESPONSE");
        verifyNoInteractions(executionService);
        verifyNoInteractions(tradeSignalService);
    }

    @Test
    @DisplayName("triggerAnalyticsCycle: the one explicit command path still executes, gated as before")
    void triggerAnalyticsCycle_stillExecutes() {
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(false);
        when(client.getOptionSummary(SYMBOL)).thenReturn(ANALYTICS_JSON);

        String result = service.triggerAnalyticsCycle(SYMBOL);

        assertThat(result).isEqualTo(ANALYTICS_JSON);
        verify(tradeSignalService).process(any(OptionAnalyticsDTO.class));
        verify(executionService).execute(any(OptionAnalyticsDTO.class));
    }

    @Test
    @DisplayName("triggerAnalyticsCycle: an already-active position blocks even the explicit trigger")
    void triggerAnalyticsCycle_activePositionBlocks() {
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(true);

        String result = service.triggerAnalyticsCycle(SYMBOL);

        assertThat(result).contains("TRADE_ALREADY_ACTIVE");
        verifyNoInteractions(executionService);
        verifyNoInteractions(client);
    }
}
