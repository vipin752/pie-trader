package com.pietrader.execution;

import com.pietrader.risk.RiskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — PositionSizingEngineTest
 *
 * Validates lot calculation for each confidence tier.
 *
 * Config (test values):
 *   base.lots = 1, max.lots = 3
 *   high.confidence = 80, very.high.confidence = 90
 */
@DisplayName("PositionSizingEngine — Confidence Tier Tests")
class PositionSizingEngineTest {

    private PositionSizingEngine engine;

    @BeforeEach
    void setUp() {
        final RiskManager riskManager = null;
        engine = new PositionSizingEngine(riskManager);
        ReflectionTestUtils.setField(engine, "defaultLotSize",            75);
        ReflectionTestUtils.setField(engine, "baseLots",                   1);
        ReflectionTestUtils.setField(engine, "maxLots",                    3);
        ReflectionTestUtils.setField(engine, "highConfidenceThreshold",   80);
        ReflectionTestUtils.setField(engine, "veryHighConfidenceThreshold", 90);
    }

    @ParameterizedTest(name = "confidence={0} → lots={1}")
    @CsvSource({
        "0,  1",   // below threshold: base lots
        "50, 1",   // below threshold: base lots
        "65, 1",   // at min confidence: base lots
        "79, 1",   // just below high: base lots
        "80, 2",   // at high: 2x base
        "85, 2",   // in high range
        "89, 2",   // just below very high
        "90, 3",   // at very high: 3x base
        "95, 3",   // above very high: still max 3
        "100, 3"   // maximum confidence: still capped at 3
    })
    @DisplayName("Confidence tier → lot calculation")
    void confidenceTierLots(int confidence, int expectedLots) {
        int lots = engine.calculate("NIFTY", confidence, null);
        assertThat(lots).isEqualTo(expectedLots);
    }

    @ParameterizedTest(name = "maxLots={0}, confidence=95 → actual={1}")
    @CsvSource({ "1, 1", "2, 2", "3, 3" })
    @DisplayName("maxLots cap enforced")
    void maxLotsCap(int maxLots, int expected) {
        ReflectionTestUtils.setField(engine, "maxLots", maxLots);
        assertThat(engine.calculate("NIFTY", 95, null)).isEqualTo(expected);
    }
}
