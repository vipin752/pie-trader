package com.pietrader.risk;

import com.pietrader.state.TradeStateManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskManager — All 6 Gate Tests")
class RiskManagerTest {

    @Mock TradeStateManager stateManager;
    @InjectMocks RiskManager riskManager;

    private static final String SYMBOL = "NIFTY";
    private static final int    GOOD_CONFIDENCE = 70;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(riskManager, "maxTradesPerDay", 5);
        ReflectionTestUtils.setField(riskManager, "maxDailyLoss",    2000.0);
        ReflectionTestUtils.setField(riskManager, "minConfidence",   65);
    }

    private void allowAll() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(false);
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(0L);
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(false);
        when(stateManager.isInCooldown(SYMBOL)).thenReturn(false);
        when(stateManager.isLocked(SYMBOL)).thenReturn(false);
    }

    // ── GATE 1: MAX LOSS ──────────────────────────────────────────────────────

    @Test @DisplayName("Gate 1 BLOCK: max loss breached")
    void gate1_maxLossBreached() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(true);
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("Max daily loss");
    }

    @Test @DisplayName("Gate 1 PASS: max loss not breached")
    void gate1_pass() {
        allowAll();
        assertThat(riskManager.check(SYMBOL, GOOD_CONFIDENCE).isAllowed()).isTrue();
    }

    // ── GATE 2: TRADE COUNT ───────────────────────────────────────────────────

    @Test @DisplayName("Gate 2 BLOCK: max trades reached")
    void gate2_maxTradesReached() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(false);
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(5L);
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("Max trades");
    }

    @Test @DisplayName("Gate 2 PASS: 4 trades done (< 5 max)")
    void gate2_belowMax() {
        allowAll();
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(4L);
        assertThat(riskManager.check(SYMBOL, GOOD_CONFIDENCE).isAllowed()).isTrue();
    }

    // ── GATE 3: ACTIVE POSITION ───────────────────────────────────────────────

    @Test @DisplayName("Gate 3 BLOCK: active position exists")
    void gate3_activePositionExists() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(false);
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(0L);
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(true);
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("Active position");
    }

    // ── GATE 4: COOLDOWN ──────────────────────────────────────────────────────

    @Test @DisplayName("Gate 4 BLOCK: in cooldown")
    void gate4_inCooldown() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(false);
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(0L);
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(false);
        when(stateManager.isInCooldown(SYMBOL)).thenReturn(true);
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("cooldown");
    }

    // ── GATE 5: LOCK ──────────────────────────────────────────────────────────

    @Test @DisplayName("Gate 5 BLOCK: trade locked")
    void gate5_tradeLocked() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(false);
        when(stateManager.getDailyTradeCount(SYMBOL)).thenReturn(0L);
        when(stateManager.hasActivePosition(SYMBOL)).thenReturn(false);
        when(stateManager.isInCooldown(SYMBOL)).thenReturn(false);
        when(stateManager.isLocked(SYMBOL)).thenReturn(true);
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("locked");
    }

    // ── GATE 6: CONFIDENCE ────────────────────────────────────────────────────

    @Test @DisplayName("Gate 6 BLOCK: confidence below threshold (60 < 65)")
    void gate6_confidenceTooLow() {
        allowAll();
        RiskCheckResult r = riskManager.check(SYMBOL, 60);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getReason()).contains("Confidence too low");
    }

    @Test @DisplayName("Gate 6 PASS: confidence exactly at threshold (65 == 65)")
    void gate6_confidenceAtThreshold() {
        allowAll();
        assertThat(riskManager.check(SYMBOL, 65).isAllowed()).isTrue();
    }

    @Test @DisplayName("Gate 6 PASS: confidence above threshold (80)")
    void gate6_confidenceAbove() {
        allowAll();
        assertThat(riskManager.check(SYMBOL, 80).isAllowed()).isTrue();
    }

    // ── ALL GATES PASS ────────────────────────────────────────────────────────

    @Test @DisplayName("ALL GATES: should pass with clean state")
    void allGatesPass() {
        allowAll();
        RiskCheckResult r = riskManager.check(SYMBOL, GOOD_CONFIDENCE);
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getReason()).contains("passed");
    }

    // ── GATE ORDERING: first breach wins ─────────────────────────────────────

    @Test @DisplayName("Gate 1 checked before Gate 2 (max loss takes priority)")
    void gateOrderingMaxLossFirst() {
        when(stateManager.isMaxLossBreached(SYMBOL)).thenReturn(true);
        // even if other gates would also block, gate 1 is checked first
        RiskCheckResult r = riskManager.check(SYMBOL, 0);
        assertThat(r.getReason()).contains("Max daily loss");
        verify(stateManager, never()).getDailyTradeCount(SYMBOL);
    }

    // ── onTradeExecuted ───────────────────────────────────────────────────────

    @Test @DisplayName("onTradeExecuted: increments count and sets last trade time")
    void onTradeExecuted_updatesState() {
        riskManager.onTradeExecuted(SYMBOL);
        verify(stateManager).incrementTradeCount(SYMBOL);
        verify(stateManager).setLastTradeTime(SYMBOL);
    }

    // ── onTradeClosed ─────────────────────────────────────────────────────────

    @Test @DisplayName("onTradeClosed: updates PnL, sets breach when loss exceeds limit")
    void onTradeClosed_setsBreachWhenMaxLossHit() {
        when(stateManager.getDailyPnl(SYMBOL)).thenReturn(-2500.0);
        riskManager.onTradeClosed(SYMBOL, -500.0);
        verify(stateManager).updateDailyPnl(SYMBOL, -500.0);
        verify(stateManager).setMaxLossBreach(SYMBOL);
    }

    @Test @DisplayName("onTradeClosed: no breach if PnL within limit")
    void onTradeClosed_noBreachWhenWithinLimit() {
        when(stateManager.getDailyPnl(SYMBOL)).thenReturn(-1000.0);
        riskManager.onTradeClosed(SYMBOL, -100.0);
        verify(stateManager).updateDailyPnl(SYMBOL, -100.0);
        verify(stateManager, never()).setMaxLossBreach(SYMBOL);
    }
}
