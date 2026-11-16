package com.pietrader.execution;

import com.pietrader.risk.RiskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * PIE TRADER — PositionSizingEngineTest
 *
 * Validates lot calculation for each confidence tier.
 *
 * Config (test values):
 *   base.lots = 1, max.lots = 3
 *   high.confidence = 80, very.high.confidence = 90
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PositionSizingEngine — Confidence Tier Tests")
class PositionSizingEngineTest {

    // calculate() now also applies RiskManager.getSizingMultiplier(symbol)
    // as a consecutive-loss sizing reduction (see PositionSizingEngine's own
    // doc comment) — a dependency this test never wired up, so every call
    // NPE'd on a null riskManager. A neutral 1.0 multiplier keeps this
    // class's confidence-tier assertions unaffected by that newer feature.
    @Mock RiskManager riskManager;

    private PositionSizingEngine engine;

    @BeforeEach
    void setUp() {
        lenient().when(riskManager.getSizingMultiplier(org.mockito.ArgumentMatchers.anyString())).thenReturn(1.0);
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
