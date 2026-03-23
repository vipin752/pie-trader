package com.pietrader.stats;

import com.pietrader.journal.entity.TradeJournalEntity;
import com.pietrader.journal.repository.TradeJournalRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * PIE TRADER — StatsServiceTest
 *
 * Validates all 11 performance metrics + Expectancy formula.
 * Scenarios: pure wins, pure losses, mixed, empty, insufficient data.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService — All 11 Metrics + Expectancy")
class StatsServiceTest {

    @Mock TradeJournalRepository journalRepo;
    @InjectMocks StatsService statsService;

    // ── Empty data ────────────────────────────────────────────────────────────

    @Test @DisplayName("No closed trades → message returned, no NPE")
    void emptyData() {
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(Collections.emptyList());
        Map<String, Object> stats = statsService.computeStats("NIFTY");
        assertThat(stats).containsKey("message");
        assertThat(stats.get("total")).isEqualTo(0);
    }

    // ── Pure win scenario ─────────────────────────────────────────────────────

    @Test @DisplayName("All wins → winRate=100%, POSITIVE edge")
    void allWins() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY", 500.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", 300.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", 700.0, "BULLISH", "BREAKOUT", "OPENING")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat((double) stats.get("winRate")).isEqualTo(100.0);
        assertThat((double) stats.get("avgLoss")).isEqualTo(0.0);
        assertThat(stats.get("edge")).isEqualTo("POSITIVE");
    }

    // ── Pure loss scenario ────────────────────────────────────────────────────

    @Test @DisplayName("All losses → winRate=0%, NEGATIVE edge")
    void allLosses() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY", -200.0, "BEARISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -300.0, "BEARISH", "MOMENTUM", "MIDDAY")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat((double) stats.get("winRate")).isEqualTo(0.0);
        assertThat(stats.get("edge")).isEqualTo("NEGATIVE");
    }

    // ── Expectancy formula ────────────────────────────────────────────────────

    @Test @DisplayName("Expectancy = (WinRate × AvgWin) − (LossRate × AvgLoss)")
    void expectancyFormula() {
        // 2 wins of ₹500, 1 loss of ₹200
        // winRate = 0.667, lossRate = 0.333
        // avgWin = 500, avgLoss = 200
        // expectancy = (0.667 × 500) - (0.333 × 200) = 333.5 - 66.6 = 266.9
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  500.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY",  500.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -200.0, "BULLISH", "MOMENTUM", "MIDDAY")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        double expectancy = (double) stats.get("expectancy");
        assertThat(expectancy).isGreaterThan(0);  // positive edge
        assertThat(expectancy).isBetween(200.0, 300.0); // rough range check
    }

    // ── RR ratio ─────────────────────────────────────────────────────────────

    @Test @DisplayName("RR = avgWin / |avgLoss|")
    void rrRatio() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  600.0, "BULLISH", "BREAKOUT", "OPENING"),
            trade("NIFTY", -200.0, "BULLISH", "BREAKOUT", "OPENING")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat((double) stats.get("avgWin")).isEqualTo(600.0);
        assertThat((double) stats.get("avgLoss")).isEqualTo(200.0);
        assertThat((double) stats.get("rr")).isEqualTo(3.0);  // 600/200
    }

    // ── Profit Factor ─────────────────────────────────────────────────────────

    @Test @DisplayName("ProfitFactor = totalWins / |totalLosses|")
    void profitFactor() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  400.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY",  200.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -200.0, "BEARISH", "MOMENTUM", "MIDDAY")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        // totalWins = 600, totalLosses = 200, PF = 3.0
        assertThat((double) stats.get("profitFactor")).isEqualTo(3.0);
    }

    // ── Best Regime ───────────────────────────────────────────────────────────

    @Test @DisplayName("Best regime = regime with highest win rate")
    void bestRegime() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  500.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY",  400.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -300.0, "BEARISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -200.0, "BEARISH", "MOMENTUM", "MIDDAY")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat(stats.get("bestRegime")).isEqualTo("BULLISH");
    }

    // ── Max Drawdown ──────────────────────────────────────────────────────────

    @Test @DisplayName("MaxDrawdown correctly computed from PnL series")
    void maxDrawdown() {
        // Running PnL: 500, 800, 500, 200, 600
        // Peak = 800, trough = 200, drawdown = 600
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  500.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY",  300.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -300.0, "BEARISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -300.0, "BEARISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY",  400.0, "BULLISH", "BREAKOUT", "OPENING")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat((double) stats.get("maxDrawdown")).isEqualTo(600.0);
    }

    // ── All required keys present ──────────────────────────────────────────────

    @Test @DisplayName("Stats map contains all 11 required contract keys")
    void allKeysPresent() {
        List<TradeJournalEntity> trades = List.of(
            trade("NIFTY",  400.0, "BULLISH", "MOMENTUM", "MIDDAY"),
            trade("NIFTY", -200.0, "BEARISH", "MOMENTUM", "CLOSING"),
            trade("NIFTY",  300.0, "BULLISH", "BREAKOUT", "OPENING")
        );
        when(journalRepo.findBySymbolOrderByRecordedAtDesc("NIFTY")).thenReturn(trades);

        Map<String, Object> stats = statsService.computeStats("NIFTY");

        assertThat(stats).containsKeys(
            "winRate", "avgWin", "avgLoss", "rr",
            "expectancy", "profitFactor", "maxDrawdown",
            "bestRegime", "bestStrategy", "bestTime", "bestStrike"
        );
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private TradeJournalEntity trade(String symbol, double pnl, String regime, String strategy, String session) {
        TradeJournalEntity t = new TradeJournalEntity();
        t.setSymbol(symbol);
        t.setPnl(pnl);
        t.setRegime(regime);
        t.setStrategy(strategy);
        t.setSessionPhase(session);
        t.setStrike(symbol + "24APR23200CE");
        t.setIsClosed(true);
        t.setRecordedAt(System.currentTimeMillis());
        return t;
    }
}
