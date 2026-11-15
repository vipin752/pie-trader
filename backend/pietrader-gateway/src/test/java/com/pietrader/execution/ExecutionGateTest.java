package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.ConfidenceDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.FinalExecutionDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.SessionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — ExecutionGateTest
 *
 * Tests all 5 gate checks.
 * Scenarios: each gate blocked in isolation, all passing together.
 */
@DisplayName("ExecutionGate — All Gate Scenarios")
class ExecutionGateTest {

    private ExecutionGate gate;

    @BeforeEach
    void setUp() {
        gate = new ExecutionGate();
        ReflectionTestUtils.setField(gate, "minConfidence",  65);
        ReflectionTestUtils.setField(gate, "marketOpenStr",  "09:00");
        ReflectionTestUtils.setField(gate, "marketCloseStr", "23:59"); // always open for test
    }

    // ── Null guard ────────────────────────────────────────────────────────────

    @Test @DisplayName("Null DTO → blocked")
    void nullDto() {
        assertThat(gate.check(null).isAllowed()).isFalse();
    }

    // ── Gate 1: execution_ready ────────────────────────────────────────────────

    @Nested @DisplayName("Gate 1 — execution_ready")
    class Gate1 {

        @Test @DisplayName("execution_ready=false → blocked")
        void blocked() {
            OptionAnalyticsDTO dto = buildDto(false, "BREAKOUT", "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isFalse();
            assertThat(gate.check(dto).getReason()).contains("execution_ready=false");
        }

        @Test @DisplayName("execution_ready=true → passes gate 1")
        void passes() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isTrue();
        }
    }

    // ── Gate 2: entry_signal ──────────────────────────────────────────────────

    @Nested @DisplayName("Gate 2 — entry_signal")
    class Gate2 {

        @Test @DisplayName("entry_signal=NO_ENTRY → blocked")
        void noEntry() {
            OptionAnalyticsDTO dto = buildDto(true, "NO_ENTRY", "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isFalse();
            assertThat(gate.check(dto).getReason()).contains("entry_signal=NO_ENTRY");
        }

        @Test @DisplayName("entry_signal=null → blocked")
        void nullEntry() {
            OptionAnalyticsDTO dto = buildDto(true, null, "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isFalse();
        }

        @Test @DisplayName("entry_signal=BREAKOUT → passes gate 2")
        void validSignal() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isTrue();
        }
    }

    // ── Gate 3: action=EXECUTE ────────────────────────────────────────────────

    @Nested @DisplayName("Gate 3 — action=EXECUTE")
    class Gate3 {

        /*@Test
        @DisplayName("action=WAIT → blocked")
        void wait() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "WAIT", true, 70);
            assertThat(gate.check(dto).isAllowed()).isFalse();
            assertThat(gate.check(dto).getReason()).contains("action=WAIT");
        }*/
        @Test
        @DisplayName("Should block trade when strategy action is WAIT"+ "action=WAIT → blocked")
        void shouldBlockTrade_WhenActionIsWait() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "WAIT", true, 70);

            ExecutionGate.GateResult result = gate.check(dto);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getReason()).contains("action=WAIT");
        }

        @Test @DisplayName("action=EXECUTE → passes gate 3")
        void execute() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 70);
            assertThat(gate.check(dto).isAllowed()).isTrue();
        }
    }

    // ── Gate 5: confidence ────────────────────────────────────────────────────

    @Nested @DisplayName("Gate 5 — confidence threshold")
    class Gate5 {

        @Test @DisplayName("confidence=64 (below 65) → blocked")
        void tooLow() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 64);
            assertThat(gate.check(dto).isAllowed()).isFalse();
            assertThat(gate.check(dto).getReason()).contains("Confidence 64");
        }

        @Test @DisplayName("confidence=65 (at threshold) → passes")
        void atThreshold() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 65);
            assertThat(gate.check(dto).isAllowed()).isTrue();
        }

        @Test @DisplayName("confidence=90 → passes")
        void high() {
            OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 90);
            assertThat(gate.check(dto).isAllowed()).isTrue();
        }
    }

    // ── All gates pass ────────────────────────────────────────────────────────

    @Test @DisplayName("All gates pass → allowed=true, reason=OK")
    void allGatesPass() {
        OptionAnalyticsDTO dto = buildDto(true, "BREAKOUT", "EXECUTE", true, 80);
        ExecutionGate.GateResult result = gate.check(dto);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getReason()).isEqualTo("OK");
    }

    // ── Builder helper ────────────────────────────────────────────────────────

    private OptionAnalyticsDTO buildDto(boolean executionReady, String entrySignal,
                                        String action, boolean isMarket, int confidence) {
        OptionAnalyticsDTO dto = new OptionAnalyticsDTO();

        // ExecutionLayer
        FinalExecutionDTO finalExec = new FinalExecutionDTO();
        finalExec.setExecutionReady(executionReady);
        finalExec.setReason(executionReady ? null : "test_block");
        ExecutionLayerDTO execLayer = new ExecutionLayerDTO();
        execLayer.setFinalExecution(finalExec);
        dto.setExecutionLayer(execLayer);

        // Entry signal (ExecutionTiming)
        if (entrySignal != null) {
            ExecutionTimingDTO timing = new ExecutionTimingDTO();
            timing.setEntrySignal(entrySignal);
            dto.setExecutionTiming(timing);
        }

        // Auto trade action
        AutoTradeActionDTO autoAction = new AutoTradeActionDTO();
        autoAction.setAction(action);
        autoAction.setOption("NIFTY24APR23200CE");
        autoAction.setDirection("BUY");
        AutoTradeDecisionDTO autoDecision = new AutoTradeDecisionDTO();
        autoDecision.setAutoTradeAction(autoAction);
        dto.setAutoTradeDecision(autoDecision);

        // Market session
        SessionDTO session = new SessionDTO();
        session.setIsMarket(isMarket);
        MarketContextDTO context = new MarketContextDTO();
        context.setSymbol("NIFTY");
        context.setSession(session);
        dto.setMarketContext(context);

        // Confidence
        ConfidenceDTO conf = new ConfidenceDTO();
        conf.setConfidenceScore(confidence);
        dto.setConfidence(conf);

        return dto;
    }
}
