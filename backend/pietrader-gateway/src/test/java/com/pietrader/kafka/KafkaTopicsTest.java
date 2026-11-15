package com.pietrader.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — KafkaTopicsTest
 *
 * Validates every Kafka topic string against the DATA CONTRACT.
 * Changing a topic string is a breaking change — this test enforces it.
 */
@DisplayName("Kafka Topics — Data Contract Validation")
class KafkaTopicsTest {

    @Test @DisplayName("CONTRACT: pie.market.ticks")
    void marketTicks()      { assertThat(KafkaTopics.MARKET_TICKS).isEqualTo("pie.market.ticks"); }

    @Test @DisplayName("CONTRACT: pie.market.state")
    void marketState()      { assertThat(KafkaTopics.MARKET_STATE).isEqualTo("pie.market.state"); }

    @Test @DisplayName("CONTRACT: pie.analytics.results")
    void analyticsResults() { assertThat(KafkaTopics.ANALYTICS_RESULTS).isEqualTo("pie.analytics.results"); }

    @Test @DisplayName("CONTRACT: pie.squareoff.signals")
    void squareoffSignals() { assertThat(KafkaTopics.SQUAREOFF_SIGNALS).isEqualTo("pie.squareoff.signals"); }

    @Test @DisplayName("CONTRACT: pie.trade.events")
    void tradeEvents()      { assertThat(KafkaTopics.TRADE_EVENTS).isEqualTo("pie.trade.events"); }

    @Test @DisplayName("CONTRACT: pie.position.events")
    void positionEvents()   { assertThat(KafkaTopics.POSITION_EVENTS).isEqualTo("pie.position.events"); }

    @Test @DisplayName("CONTRACT: pie.alerts")
    void alerts()           { assertThat(KafkaTopics.ALERTS).isEqualTo("pie.alerts"); }

    @Test @DisplayName("All topics start with 'pie.'")
    void allTopicsHavePrefix() {
        assertThat(KafkaTopics.MARKET_TICKS).startsWith("pie.");
        assertThat(KafkaTopics.MARKET_STATE).startsWith("pie.");
        assertThat(KafkaTopics.ANALYTICS_RESULTS).startsWith("pie.");
        assertThat(KafkaTopics.SQUAREOFF_SIGNALS).startsWith("pie.");
        assertThat(KafkaTopics.TRADE_EVENTS).startsWith("pie.");
        assertThat(KafkaTopics.POSITION_EVENTS).startsWith("pie.");
        assertThat(KafkaTopics.ALERTS).startsWith("pie.");
    }
}
