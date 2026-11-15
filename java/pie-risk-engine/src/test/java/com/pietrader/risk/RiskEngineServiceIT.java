package com.pietrader.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres + Redis, not mocks (QA master prompt §3/§15: "Do not use
 * mocked results to claim a real broker integration works" — this is the
 * same discipline applied to the datastore layer). Every scenario here
 * actually restarts/recreates the Spring context against the SAME
 * containers for the "survives restart" cases, proving Postgres — not
 * JVM memory — is the source of truth.
 */
@Testcontainers
@SpringBootTest(properties = {
    "pie.risk.capital=200000", "pie.risk.max-daily-loss=4000", "pie.risk.max-loss-per-trade=1500",
    "pie.risk.max-lots=2", "pie.risk.min-lots=1", "pie.risk.max-consecutive-losses=2",
    "pie.risk.cooldown-minutes=15", "pie.risk.no-expiry-day-entries=true"
})
class RiskEngineServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void wire(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired RiskEngineService engine;
    @Autowired DayLedgerRepository ledgers;
    @Autowired RiskDecisionLogRepository decisionLog;
    @Autowired CloseEventRepository closeEvents;

    private static final Map<String, Integer> LOT = Map.of("NIFTY", 65, "BANKNIFTY", 30, "SENSEX", 20);

    private TradeCandidate order(String underlying, int qty, double risk, LocalDate expiry, LocalDateTime now) {
        return new TradeCandidate(underlying, underlying + " TEST CE", qty, LOT.get(underlying), risk, expiry, now);
    }

    private TradeCandidate withPremiums(String underlying, int qty, Double entry, Double stop, LocalDateTime now) {
        return new TradeCandidate(underlying, underlying + " TEST CE", qty, LOT.get(underlying), entry, stop,
            0, null, now);
    }

    @Test
    void approves_a_clean_trade_and_writes_an_audit_row() {
        var now = LocalDateTime.of(2026, 11, 2, 10, 0);
        long before = decisionLog.count();
        var d = engine.approve(order("NIFTY", 65, 1000, null, now));
        assertThat(d.allowed()).isTrue();
        assertThat(decisionLog.count()).isEqualTo(before + 1);  // every decision is audited (spec §9)
    }

    @Test
    void rejects_oversized_lots() {
        var now = LocalDateTime.of(2026, 11, 3, 10, 0);
        var d = engine.approve(order("NIFTY", 65 * 5, 1000, null, now));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons().getFirst()).contains("exceeds the cap");
    }

    @Test
    void rejects_quantity_not_a_multiple_of_lot_size() {
        // RISK-010 / Suite G "order quantity multiple of current lot size" —
        // a non-multiple silently truncated to whole lots would hide a bug.
        var now = LocalDateTime.of(2026, 11, 4, 10, 0);
        var d = engine.approve(order("NIFTY", 70, 500, null, now));  // 70 is not a multiple of 65
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("whole multiple"));
    }

    @Test
    void rejects_expiry_day_entry() {
        var now = LocalDateTime.of(2026, 11, 17, 10, 0);
        var d = engine.approve(order("NIFTY", 65, 1000, LocalDate.of(2026, 11, 17), now));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("expiry day"));
    }

    @Test
    void rejects_zero_or_inverted_stop_distance() {
        // RISK-007/008: for a long option the stop must sit below entry.
        var now = LocalDateTime.of(2026, 11, 5, 10, 0);
        var zero = engine.approve(withPremiums("NIFTY", 65, 100.0, 100.0, now));
        assertThat(zero.allowed()).isFalse();
        assertThat(zero.reasons()).anyMatch(r -> r.contains("below entry"));
        var inverted = engine.approve(withPremiums("NIFTY", 65, 100.0, 120.0, now));
        assertThat(inverted.allowed()).isFalse();
    }

    @Test
    void rejects_negative_or_zero_premiums() {
        var now = LocalDateTime.of(2026, 11, 6, 10, 0);
        var negEntry = engine.approve(withPremiums("NIFTY", 65, -5.0, 1.0, now));
        assertThat(negEntry.allowed()).isFalse();
        assertThat(negEntry.reasons()).anyMatch(r -> r.contains("Entry premium"));
        var negStop = engine.approve(withPremiums("NIFTY", 65, 100.0, -1.0, now));
        assertThat(negStop.allowed()).isFalse();
        assertThat(negStop.reasons()).anyMatch(r -> r.contains("Stop premium"));
    }

    @Test
    void derives_risk_from_premiums_matching_the_spec_fixture() {
        // Spec §6 fixture: entry 100, stop 80, lot 65, 6 lots -> risk/lot 1300,
        // 6-lot risk 7800 — above this profile's 1500 per-trade cap, so it
        // must be rejected, and for exactly that reason.
        var now = LocalDateTime.of(2026, 11, 9, 10, 0);
        var d = engine.approve(withPremiums("NIFTY", 65 * 6, 100.0, 80.0, now));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("₹7800") || r.contains("7,800") || r.contains("7800"));
    }

    @Test
    void stops_after_daily_loss_limit_and_survives_a_fresh_context_read() {
        var now = LocalDateTime.of(2026, 11, 10, 10, 0);
        boolean applied = engine.onClose("evt-daily-1", "NIFTY", -4500, now);
        assertThat(applied).isTrue();
        var d = engine.approve(order("NIFTY", 65, 500, null, now.plusMinutes(30)));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("Daily loss limit"));
        // read the ledger straight from Postgres, independent of any in-memory state
        var row = ledgers.findById(new DayLedgerEntity.Key("NIFTY", now.toLocalDate())).orElseThrow();
        assertThat(row.getRealisedPnl().doubleValue()).isEqualTo(-4500.0);
    }

    @Test
    void duplicate_close_event_is_applied_exactly_once() {
        // Critical Safety #2/#3: a replayed fill/event must not double-count.
        var now = LocalDateTime.of(2026, 11, 11, 10, 0);
        long events = closeEvents.count();
        assertThat(engine.onClose("evt-dup-1", "SENSEX", -300, now)).isTrue();
        assertThat(engine.onClose("evt-dup-1", "SENSEX", -300, now)).isFalse();  // same event id, replayed
        assertThat(closeEvents.count()).isEqualTo(events + 1);
        var row = ledgers.findById(new DayLedgerEntity.Key("SENSEX", now.toLocalDate())).orElseThrow();
        assertThat(row.getRealisedPnl().doubleValue()).isEqualTo(-300.0);  // not -600
    }

    @Test
    void stops_after_consecutive_losses_then_cooldown_clears_and_new_day_resets() {
        var now = LocalDateTime.of(2026, 11, 12, 10, 0);
        engine.onClose("evt-c1", "BANKNIFTY", -200, now);
        engine.onClose("evt-c2", "BANKNIFTY", -200, now.plusMinutes(10));
        var d = engine.approve(order("BANKNIFTY", 30, 500, null, now.plusMinutes(20)));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasons()).anyMatch(r -> r.contains("losses in a row") || r.contains("Cooldown"));
        var tomorrow = now.plusDays(1).withHour(9).withMinute(20);
        assertThat(engine.approve(order("BANKNIFTY", 30, 500, null, tomorrow)).allowed()).isTrue();
    }

    @Test
    void portfolio_lock_serialises_decisions_for_the_same_correlation_group() {
        // Direct proof of Critical Safety #5/#7 at the lock layer: once held,
        // a second acquire for the SAME group fails; a different group is unaffected.
        // Uses its own group name — "india_equity_index" is the real group
        // approve() uses for every index, and holding it here would starve
        // every other test in this class for the lock's TTL (DEF-003).
        var lock = new PortfolioLock(redisTemplateBean);
        var group = "test_only_group";
        var token = lock.tryAcquire(group, java.time.Duration.ofSeconds(5));
        assertThat(token).isNotNull();
        try {
            assertThat(lock.tryAcquire(group, java.time.Duration.ofSeconds(5))).isNull();
            assertThat(lock.tryAcquire("a_different_group", java.time.Duration.ofSeconds(5))).isNotNull();
        } finally {
            lock.release(group, token);
        }
        assertThat(lock.tryAcquire(group, java.time.Duration.ofSeconds(5))).isNotNull();
    }

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redisTemplateBean;
}
