package com.pietrader.marketdata.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.market.MarketStateDTO;
import com.pietrader.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * PIE TRADER — MarketStateKafkaProducer
 *
 * Publishes a fully-derived MarketStateDTO to Kafka topic: pie.market.state
 *
 * Flow: AngelWebSocketClient → TickProcessor → MarketStateBuilder
 *       → MarketStateKafkaProducer → pie.market.state → Python
 *
 * Key = symbol (for partition routing — all NIFTY ticks to same partition)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStateKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    /**
     * Publish a MarketState snapshot to Kafka.
     * Called by MarketStateBuilder after every option chain update cycle.
     */
    public void publish(MarketStateDTO state) {
        if (state == null || state.getSymbol() == null) return;
        try {
            String json = objectMapper.writeValueAsString(state);
            kafkaTemplate.send(KafkaTopics.MARKET_STATE, state.getSymbol(), json);
            log.debug("📡 MarketState → {} | spot={} atm={} pcr={} regime={}",
                state.getSymbol(), state.getSpot(), state.getAtm(),
                state.getPcr(), state.getRegime());
        } catch (Exception e) {
            log.error("❌ MarketState publish failed for {}: {}", state.getSymbol(), e.getMessage(), e);
        }
    }
}
