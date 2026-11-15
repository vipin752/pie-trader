package com.pietrader.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RiskEngineServiceTest {

    private final RiskEngineService engine = new RiskEngineService(
        new RiskRules(200_000, 4_000, 1_500, 2, 1, 2, 15, true));

    private static final java.util.Map<String, Integer> LOT = java.util.Map.of(
        "NIFTY", 65, "BANKNIFTY", 30, "SENSEX", 20);

    private TradeCandidate order(String underlying, int qty, double risk, LocalDate expiry, LocalDateTime now) {
        return new TradeCandidate(underlying, underlying + " TEST CE", qty, LOT.get(underlying), risk, expiry, now);
    }

    @Test
    void approves_a_clean_trade() {
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        var d = engine.approve(order("NIFTY", 65, 1000, null, now));
        assertThat(d.allowed()).isTrue();
        assertThat(d.reasons()).isEmpty();
    }

    @Test
    void rejects_oversized_lots() {
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        var d = engine.approve(order("NIFTY", 65 * 5, 1000, null, now));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons().getFirst()).contains("exceeds the cap");
    }

    @Test
    void rejects_expiry_day_entry() {
        var now = LocalDateTime.of(2026, 11, 17, 10, 0);
        var d = engine.approve(order("NIFTY", 65, 1000, LocalDate.of(2026, 11, 17), now));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("expiry day"));
    }

    @Test
    void stops_after_daily_loss_limit() {
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        engine.onClose("NIFTY", -4500, now);
        var d = engine.approve(order("NIFTY", 65, 500, null, now.plusMinutes(30)));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("Daily loss limit"));
    }

    @Test
    void stops_after_consecutive_losses() {
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        engine.onClose("BANKNIFTY", -200, now);
        engine.onClose("BANKNIFTY", -200, now.plusMinutes(10));
        var d = engine.approve(order("BANKNIFTY", 30, 500, null, now.plusMinutes(20)));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("losses in a row"));
    }

    @Test
    void enforces_cooldown_after_a_loss_then_clears() {
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        engine.onClose("SENSEX", -300, now);
        assertThat(engine.approve(order("SENSEX", 20, 500, null, now.plusMinutes(5))).allowed()).isFalse();
        assertThat(engine.approve(order("SENSEX", 20, 500, null, now.plusMinutes(20))).allowed()).isTrue();
    }

    @Test
    void new_trading_day_resets_the_ledger() {
        var day1 = LocalDateTime.of(2026, 11, 16, 15, 0);
        engine.onClose("NIFTY", -4500, day1);
        var day2 = LocalDateTime.of(2026, 11, 17, 9, 20);
        assertThat(engine.approve(order("NIFTY", 65, 500, null, day2)).allowed()).isTrue();
    }

    @Test
    void rejects_below_minimum_lots() {
        var strictMin = new RiskEngineService(new RiskRules(200_000, 4_000, 1_500, 10, 6, 2, 15, true));
        var now = LocalDateTime.of(2026, 11, 16, 10, 0);
        var d = strictMin.approve(order("NIFTY", 65, 500, null, now));  // 1 lot, min is 6 (spec §7/§12)
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("minimum position"));
    }
}
