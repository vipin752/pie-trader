package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.ConfidenceDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.dealer.GammaDTO;
import com.pietrader.dto.market.CompressionDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.MarketStructureDTO;
import com.pietrader.dto.volatility.VolatilityContextDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.risk.RiskManager;
import com.pietrader.service.SquareOffService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — ExitEngineMarketConditionTest
 *
 * Tests all 13 exit rules from the data contract §7:
 *   1–6: Basic rules (from previous delivery)
 *   7.  Compression → quick profit exit
 *   8.  Expansion → trail switch
 *   9.  Negative gamma → hold (no exit even in profit)
 *   10. Positive gamma + profit → quick exit
 *   11. High IV + profit → fast exit
 *   12. Low IV → hold (don't activate trailing early)
 *   13. EOD handled by TradingScheduler (time-based, tested separately)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExitEngine — 13 Exit Rules (Market Condition Based)")
class ExitEngineMarketConditionTest {

    @Mock PositionManager    positionManager;
    @Mock SquareOffService   squareOffService;
    @Mock RiskManager        riskManager;
    @Mock TradeJournalFacade journalService;

    @InjectMocks ExitEngine exitEngine;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exitEngine, "trailingActivatePct", 20.0);
        ReflectionTestUtils.setField(exitEngine, "trailingLockPct",     10.0);
        ReflectionTestUtils.setField(exitEngine, "timeExitStr",         "23:59"); // disabled
        // NOTE: ExitEngine has no `highIvThreshold` field — isHighIv()/isLowIv()
        // compare the volatility-regime STRING directly ("HIGH_IV"/"LOW_IV"),
        // not a numeric threshold. The line that used to try to set one here
        // crashed every test in this class at setUp() before it could run.
    }

    // ── Rule 7: Compression → quick profit exit ───────────────────────────────

    @Test @DisplayName("Rule 7: SIDEWAYS regime + in profit → COMPRESSION_QUICK_PROFIT exit")
    void compressionQuickProfit() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        // Price at 220 (in profit), regime = SIDEWAYS (compression)
        OptionAnalyticsDTO dto = tickDto("NIFTY", 220.0, "SIDEWAYS", "NEUTRAL_IV", null);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService).squareOff(eq("NIFTY"), eq("COMPRESSION_QUICK_PROFIT"));
    }

    // ── Rule 9: Negative gamma → hold winners ────────────────────────────────

    @Test @DisplayName("Rule 9: Negative gamma + in profit → no quick exit (hold)")
    void negativeGammaHold() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        // Price in profit, negative gamma → should NOT exit
        OptionAnalyticsDTO dto = tickDto("NIFTY", 220.0, "BULLISH", "LOW_IV", -5000.0);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService, never()).squareOff(any(), any());
    }

    // ── Rule 10: Positive gamma → quick exit ──────────────────────────────────

    @Test @DisplayName("Rule 10: Positive gamma + in profit → POSITIVE_GAMMA_QUICK_EXIT")
    void positiveGammaQuickExit() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        // Price in profit, positive gamma → quick exit
        OptionAnalyticsDTO dto = tickDto("NIFTY", 220.0, "BULLISH", "LOW_IV", 3000.0);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService).squareOff(eq("NIFTY"), eq("POSITIVE_GAMMA_QUICK_EXIT"));
    }

    // ── Rule 11: High IV → fast exit ─────────────────────────────────────────

    @Test @DisplayName("Rule 11: HIGH_IV regime + in profit → HIGH_IV_FAST_EXIT")
    void highIvFastExit() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        OptionAnalyticsDTO dto = tickDto("NIFTY", 220.0, "BULLISH", "HIGH_IV", null);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService).squareOff(eq("NIFTY"), eq("HIGH_IV_FAST_EXIT"));
    }

    // ── Rule 12: Low IV → hold ────────────────────────────────────────────────

    @Test @DisplayName("Rule 12: LOW_IV + moderate gain → no exit (hold)")
    void lowIvHold() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        // Only 10% gain, LOW_IV regime — exit engine should hold
        OptionAnalyticsDTO dto = tickDto("NIFTY", 210.0, "BULLISH", "LOW_IV", null);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService, never()).squareOff(any(), any());
    }

    // ── Rules 1 & 2 still work ───────────────────────────────────────────────

    @Test @DisplayName("Rule 1: SL still fires regardless of market regime")
    void slTrumpsAll() {
        registerTrade("NIFTY", 200.0, 160.0, 400.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        // Price below SL, regime = BULLISH (doesn't matter — SL wins)
        OptionAnalyticsDTO dto = tickDto("NIFTY", 150.0, "BULLISH", "LOW_IV", null);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService).squareOff(eq("NIFTY"), eq("STOP_LOSS"));
    }

    @Test @DisplayName("Rule 2: Target still fires regardless of market regime")
    void targetTrumpsAll() {
        registerTrade("NIFTY", 200.0, 160.0, 320.0);
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY"));

        OptionAnalyticsDTO dto = tickDto("NIFTY", 325.0, "BULLISH", "HIGH_IV", null);
        exitEngine.onAnalyticsTick("NIFTY", dto);
        exitEngine.runExitChecks();

        verify(squareOffService).squareOff(eq("NIFTY"), eq("TARGET_HIT"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerTrade(String symbol, double entry, double sl, double target) {
        Trade trade = Trade.builder()
            .tradeId("T1").orderId("O1").symbol(symbol)
            .strike(symbol + "24APR23200CE").direction("BUY")
            .lots(1).quantity(75).entryPrice(entry).sl(sl).target(target)
            .mode(TradeMode.PAPER).confidence(80).success(true).build();
        exitEngine.register(trade, null);
    }

    private OptionAnalyticsDTO tickDto(String symbol, double ltp, String regime,
                                        String ivRegime, Double gamma) {
        // NOTE: MarketContextDTO has no `regime` field and VolatilityContextDTO
        // has no `ivRegime` field (see ExitEngine's own doc comments: the real
        // IV regime path is DealerInventoryModelDTO.volatilityRegime via
        // DealerPositioningDTO, not VolatilityContextDTO). `regime` ("SIDEWAYS")
        // maps onto the one real compression signal ExitEngine reads —
        // MarketStructureDTO.compression.compressionDetected.
        MarketContextDTO ctx = new MarketContextDTO();
        ctx.setSymbol(symbol); ctx.setSpot(ltp);

        VolatilityContextDTO vol = new VolatilityContextDTO();

        GammaDTO gammaDto = new GammaDTO();
        gammaDto.setNetGamma(gamma);
        DealerInventoryModelDTO inv = new DealerInventoryModelDTO();
        inv.setVolatilityRegime(ivRegime);
        // gammaFlip left null (no active flip zone): ExitEngine.isGammaFlip()
        // treats ANY non-null positive value as an active flip, which would
        // short-circuit evaluate() before the compression/gamma/IV rules
        // under test here are ever reached — see the GAMMA_FLIP trigger
        // test in ExitEngineTest for that rule covered on its own.
        DealerPositioningDTO dealer = new DealerPositioningDTO();
        dealer.setGamma(gammaDto);
        dealer.setDealerInventoryModel(inv);

        OptionAnalyticsDTO dto = new OptionAnalyticsDTO();
        dto.setMarketContext(ctx);
        dto.setVolatilityContext(vol);
        dto.setDealerPositioning(dealer);

        if ("SIDEWAYS".equalsIgnoreCase(regime)) {
            CompressionDTO compression = CompressionDTO.builder().compressionDetected(true).build();
            dto.setMarketStructure(MarketStructureDTO.builder().compression(compression).build());
        }

        ConfidenceDTO conf = new ConfidenceDTO();
        conf.setConfidenceScore(50); // below threshold so no opposite signal trigger
        dto.setConfidence(conf);
        return dto;
    }
}
