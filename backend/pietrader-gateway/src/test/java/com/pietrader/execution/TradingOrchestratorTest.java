package com.pietrader.execution;

import com.pietrader.dto.ConfidenceDTO;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.execution.FinalExecutionDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.SessionDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — TradingOrchestratorTest
 *
 * Validates the full 9-step execution flow.
 * Each test verifies exactly which downstream services are or aren't called.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradingOrchestrator — Full Flow Scenarios")
class TradingOrchestratorTest {

    @Mock ModeManager          modeManager;
    @Mock ExecutionGate        executionGate;
    @Mock RiskManagerFacade    riskManager;
    @Mock PositionSizingEngine sizingEngine;
    @Mock OrderManager         orderManager;
    @Mock PositionManager      positionManager;
    @Mock ExitEngine           exitEngine;
    @Mock TradeJournalFacade   journalService;

    @InjectMocks
    TradingOrchestrator orchestrator;

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test @DisplayName("Null DTO → nothing called")
    void nullDto() {
        orchestrator.resolveAction(null);
        verifyNoInteractions(modeManager, executionGate, riskManager);
    }

    // ── Mode gate ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("Gate 1 — Manual mode")
    class ManualModeGate {

        @Test @DisplayName("MANUAL mode → orchestrator returns early, gate not checked")
        void manualModeSkips() {
            when(modeManager.isManualMode()).thenReturn(true);
            orchestrator.resolveAction(buildValidDto());
            verify(modeManager).isManualMode();
            verifyNoInteractions(executionGate, riskManager, orderManager);
        }

        @Test @DisplayName("AUTO mode → gate is checked")
        void autoModeContinues() {
            when(modeManager.isManualMode()).thenReturn(false);
            when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.block("test"));
            orchestrator.resolveAction(buildValidDto());
            verify(executionGate).check(any());
        }
    }

    // ── ExecutionGate block ───────────────────────────────────────────────────

    @Test @DisplayName("ExecutionGate blocked → risk not checked")
    void gateBlocked() {
        when(modeManager.isManualMode()).thenReturn(false);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.block("execution_ready=false"));
        orchestrator.resolveAction(buildValidDto());
        verifyNoInteractions(riskManager, orderManager, positionManager);
    }

    // ── Risk block ────────────────────────────────────────────────────────────

    @Test @DisplayName("Risk blocked → order not placed")
    void riskBlocked() {
        when(modeManager.isManualMode()).thenReturn(false);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.allow());
        when(riskManager.check(anyString(), anyInt()))
            .thenReturn(RiskManagerFacade.RiskResult.block("max trades hit"));
        orchestrator.resolveAction(buildValidDto());
        verifyNoInteractions(orderManager, positionManager, exitEngine, journalService);
    }

    // ── Sizing returns 0 ──────────────────────────────────────────────────────

    @Test @DisplayName("PositionSizing returns 0 → order not placed")
    void zeroLots() {
        when(modeManager.isManualMode()).thenReturn(false);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.allow());
        when(riskManager.check(anyString(), anyInt())).thenReturn(RiskManagerFacade.RiskResult.allow());
        when(sizingEngine.calculate(anyString(), anyInt(), any())).thenReturn(0);
        orchestrator.resolveAction(buildValidDto());
        verifyNoInteractions(orderManager, positionManager, exitEngine);
    }

    // ── Order failure ─────────────────────────────────────────────────────────

    @Test @DisplayName("OrderManager returns failed trade → position not opened")
    void orderFailed() {
        Trade failedTrade = Trade.builder().success(false).failureReason("broker reject").build();
        when(modeManager.isManualMode()).thenReturn(false);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.allow());
        when(riskManager.check(anyString(), anyInt())).thenReturn(RiskManagerFacade.RiskResult.allow());
        when(sizingEngine.calculate(anyString(), anyInt(), any())).thenReturn(1);
        when(orderManager.execute(any())).thenReturn(failedTrade);
        orchestrator.resolveAction(buildValidDto());
        verifyNoInteractions(positionManager, exitEngine, journalService);
    }

    // ── Full success ──────────────────────────────────────────────────────────

    @Test @DisplayName("All gates pass + order success → full pipeline executed")
    void fullSuccess() {
        Trade successTrade = Trade.builder()
            .tradeId("t1").orderId("O1").symbol("NIFTY")
            .strike("NIFTY24APR23200CE").direction("BUY")
            .lots(1).quantity(75).entryPrice(186.6).sl(130.0).target(280.0)
            .mode(TradeMode.PAPER).confidence(80).success(true)
            .build();

        when(modeManager.isManualMode()).thenReturn(false);
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.allow());
        when(riskManager.check(anyString(), anyInt())).thenReturn(RiskManagerFacade.RiskResult.allow());
        when(sizingEngine.calculate(anyString(), anyInt(), any())).thenReturn(1);
        when(orderManager.execute(any())).thenReturn(successTrade);

        orchestrator.resolveAction(buildValidDto());

        verify(positionManager).openPosition(eq(successTrade), any());
        verify(exitEngine).register(eq(successTrade), any());
        verify(journalService).record(eq(successTrade), any());
    }

    // ── Exception resilience ──────────────────────────────────────────────────

    @Test @DisplayName("Exception in OrderManager → does not propagate")
    void exceptionDoesNotPropagate() {
        when(modeManager.isManualMode()).thenReturn(false);
        when(executionGate.check(any())).thenReturn(ExecutionGate.GateResult.allow());
        when(riskManager.check(anyString(), anyInt())).thenReturn(RiskManagerFacade.RiskResult.allow());
        when(sizingEngine.calculate(anyString(), anyInt(), any())).thenReturn(1);
        when(orderManager.execute(any())).thenThrow(new RuntimeException("broker down"));
        // Should NOT throw
        orchestrator.resolveAction(buildValidDto());
    }

    // ── DTO builder ───────────────────────────────────────────────────────────

    private OptionAnalyticsDTO buildValidDto() {
        OptionAnalyticsDTO dto = new OptionAnalyticsDTO();

        FinalExecutionDTO fe = new FinalExecutionDTO();
        fe.setExecutionReady(true);
        ExecutionLayerDTO el = new ExecutionLayerDTO();
        el.setFinalExecution(fe);
        dto.setExecutionLayer(el);

        ExecutionTimingDTO et = new ExecutionTimingDTO();
        et.setEntrySignal("BREAKOUT");
        dto.setExecutionTiming(et);

        AutoTradeActionDTO action = new AutoTradeActionDTO();
        action.setAction("EXECUTE");
        action.setOption("NIFTY24APR23200CE");
        action.setDirection("BUY");
        AutoTradeDecisionDTO atd = new AutoTradeDecisionDTO();
        atd.setAutoTradeAction(action);
        dto.setAutoTradeDecision(atd);

        SessionDTO session = new SessionDTO();
        session.setIsMarket(true);
        MarketContextDTO ctx = new MarketContextDTO();
        ctx.setSymbol("NIFTY");
        ctx.setSpot(23114.5);
        ctx.setSession(session);
        dto.setMarketContext(ctx);

        ConfidenceDTO conf = new ConfidenceDTO();
        conf.setConfidenceScore(80);
        dto.setConfidence(conf);

        return dto;
    }
}
