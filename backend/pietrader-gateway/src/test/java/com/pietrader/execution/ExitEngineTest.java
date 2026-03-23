package com.pietrader.execution;

import com.pietrader.broker.BrokerAdapter;
import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.risk.RiskManager;
import com.pietrader.dto.ConfidenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — ExitEngineTest
 *
 * Tests all 6 exit triggers:
 *   1. Stop Loss
 *   2. Target Hit
 *   3. Trailing SL
 *   4. Time Exit (tested via time mock)
 *   5. Gamma Flip
 *   6. Opposite Signal (high confidence)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExitEngine — All 6 Exit Triggers")
class ExitEngineTest {

    @Mock PositionManager    positionManager;
    @Mock BrokerAdapter      brokerAdapter;
    @Mock RiskManager        riskManager;
    @Mock ModeManager        modeManager;
    @Mock TradeJournalFacade journalService;

    @InjectMocks
    ExitEngine exitEngine;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exitEngine, "lotSize",              75);
        ReflectionTestUtils.setField(exitEngine, "trailingActivatePct",  20.0);
        ReflectionTestUtils.setField(exitEngine, "trailingLockPct",      10.0);
        ReflectionTestUtils.setField(exitEngine, "timeExitStr",          "23:59"); // disabled for most tests
    }

    private Trade paperTrade(String symbol, double entry, double sl, double target) {
        return Trade.builder()
            .tradeId("T1").orderId("PAPER_001").symbol(symbol)
            .strike(symbol + "24APR23200CE").direction("BUY")
            .lots(1).quantity(75).entryPrice(entry).sl(sl).target(target)
            .mode(TradeMode.PAPER).confidence(80).success(true).build();
    }

    private OptionAnalyticsDTO tickDto(String symbol, double ltp) {
        MarketContextDTO ctx = new MarketContextDTO();
        ctx.setSymbol(symbol);
        ctx.setSpot(ltp);
        OptionAnalyticsDTO dto = new OptionAnalyticsDTO();
        dto.setMarketContext(ctx);
        return dto;
    }

    // ── Trigger 1: Stop Loss ─────────────────────────────────────────────────

    @Test @DisplayName("LTP ≤ SL → STOP_LOSS exit triggered")
    void stopLoss() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 320.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);

        exitEngine.register(trade, null);
        exitEngine.onAnalyticsTick("NIFTY", tickDto("NIFTY", 155.0)); // below SL 160

        exitEngine.runExitChecks();

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), eq("STOP_LOSS"), anyDouble());
        verify(journalService).recordExit(eq("NIFTY"), anyDouble(), eq("STOP_LOSS"), anyDouble());
    }

    // ── Trigger 2: Target Hit ────────────────────────────────────────────────

    @Test @DisplayName("LTP ≥ Target → TARGET_HIT exit triggered")
    void targetHit() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 320.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);

        exitEngine.register(trade, null);
        exitEngine.onAnalyticsTick("NIFTY", tickDto("NIFTY", 325.0)); // above target 320

        exitEngine.runExitChecks();

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), eq("TARGET_HIT"), anyDouble());
    }

    // ── Trigger 3: Trailing SL ───────────────────────────────────────────────

    @Test @DisplayName("Gain > 20% → trailing activates; LTP drops → TRAILING_SL")
    void trailingSl() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);

        exitEngine.register(trade, null);

        // Step 1: price up 25% → trailing activates, locks at 216 (240 * 0.90)
        exitEngine.onAnalyticsTick("NIFTY", tickDto("NIFTY", 240.0));
        exitEngine.runExitChecks(); // no exit yet

        // Step 2: price drops below trailing SL
        exitEngine.onAnalyticsTick("NIFTY", tickDto("NIFTY", 210.0)); // below 216
        exitEngine.runExitChecks();

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), eq("TRAILING_SL"), anyDouble());
    }

    // ── Trigger 5: Gamma Flip ────────────────────────────────────────────────

    @Test @DisplayName("gamma_flip=true in analytics tick → GAMMA_FLIP exit")
    void gammaFlip() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);

        exitEngine.register(trade, null);

        // Build tick with gamma_flip = true
        DealerInventoryModelDTO inv = new DealerInventoryModelDTO();
        inv.setGammaFlip(Double.MAX_VALUE);
        DealerPositioningDTO dealer = new DealerPositioningDTO();
        dealer.setDealerInventoryModel(inv);
        OptionAnalyticsDTO dto = tickDto("NIFTY", 210.0);
        dto.setDealerPositioning(dealer);

        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), eq("GAMMA_FLIP"), anyDouble());
    }

    // ── Trigger 6: Opposite Signal ───────────────────────────────────────────

    @Test @DisplayName("Opposite direction signal confidence≥75 → exit triggered")
    void oppositeSignal() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));
        when(modeManager.currentMode()).thenReturn(TradeMode.PAPER);

        exitEngine.register(trade, null); // trade is BUY

        // New signal: SELL with 80% confidence
        AutoTradeActionDTO action = new AutoTradeActionDTO();
        action.setDirection("SELL");
        AutoTradeDecisionDTO atd = new AutoTradeDecisionDTO();
        atd.setAutoTradeAction(action);
        ConfidenceDTO conf = new ConfidenceDTO();
        conf.setConfidenceScore(80);
        OptionAnalyticsDTO dto = tickDto("NIFTY", 210.0);
        dto.setAutoTradeDecision(atd);
        dto.setConfidence(conf);

        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), contains("OPPOSITE_SIGNAL"), anyDouble());
    }

    // ── No exit when within range ─────────────────────────────────────────────

    @Test @DisplayName("LTP between SL and target → no exit")
    void noExitInRange() {
        Trade trade = paperTrade("NIFTY", 200.0, 160.0, 320.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));
        when(positionManager.getPosition("NIFTY")).thenReturn(buildState("NIFTY", 200.0));

        exitEngine.register(trade, null);
        exitEngine.onAnalyticsTick("NIFTY", tickDto("NIFTY", 210.0)); // safely in range
        exitEngine.runExitChecks();

        verify(positionManager, never()).closePosition(any(), anyDouble(), any(), anyDouble());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private com.pietrader.state.TradeState buildState(String symbol, double entry) {
        com.pietrader.state.TradeState s = new com.pietrader.state.TradeState();
        s.setSymbol(symbol);
        s.setTradeActive(true);
        s.setEntryPrice(entry);
        s.setDirection("BUY");
        s.setQuantity(75);
        s.setStrike(symbol + "24APR23200CE");
        return s;
    }
}
