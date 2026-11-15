package com.pietrader.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Pure unit tests — no Spring context, no containers, runs in milliseconds. */
class TradeCandidateTest {

    @Test
    void falls_back_to_riskInr_when_no_premiums_given() {
        var c = new TradeCandidate("NIFTY", "NIFTY TEST CE", 65, 65, 1234.0, null, LocalDateTime.now());
        assertThat(c.hasPremiums()).isFalse();
        assertThat(c.effectiveRisk()).isEqualTo(1234.0);
    }

    @Test
    void derives_risk_from_premiums_when_both_given() {
        // Spec §6 fixture: (100 - 80) * 65 * 6 lots = 7800
        var c = new TradeCandidate("NIFTY", "NIFTY TEST CE", 65 * 6, 65, 100.0, 80.0, 0, null, LocalDateTime.now());
        assertThat(c.hasPremiums()).isTrue();
        assertThat(c.effectiveRisk()).isEqualTo(7800.0);
    }

    @Test
    void increasing_lots_never_reduces_planned_risk() {
        // Suite 20 property: "Increasing the number of lots must not reduce total planned risk."
        double prev = -1;
        for (int lots = 1; lots <= 10; lots++) {
            var c = new TradeCandidate("NIFTY", "NIFTY TEST CE", lots * 65, 65, 100.0, 80.0, 0, null, LocalDateTime.now());
            assertThat(c.effectiveRisk()).isGreaterThan(prev);
            prev = c.effectiveRisk();
        }
    }
}
