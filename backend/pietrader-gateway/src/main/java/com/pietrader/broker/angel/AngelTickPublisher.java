package com.pietrader.broker.angel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.marketdata.MarketStateBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * PIE TRADER — AngelTickPublisher
 *
 * Publishes parsed tick JSON to Kafka pie.market.ticks.
 * Python KafkaConsumerService reads from this topic.
 *
 * GAP-2 FIX: Now also feeds MarketStateBuilder.onTick() on every tick.
 *   MarketStateBuilder accumulates option ticks and publishes a derived
 *   MarketStateDTO (gamma, PCR, regime, IV, support/resistance) to
 *   pie.market.state every 2 seconds for Python intelligence engines.
 *   Without this hook, pie.market.state had 0 messages — Python
 *   MarketStateConsumer received nothing.
 *
 * Health fix: Records system:last_tick_time in Redis for /api/system/health.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AngelTickPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate           redis;
    private final MarketStateBuilder            marketStateBuilder; // GAP-2 FIX
    private final ObjectMapper                  objectMapper;

    private static final String TOPIC         = "pie.market.ticks";
    private static final String LAST_TICK_KEY = "system:last_tick_time";

    public void publish(String tickJson) {
        try {
            // Step 1 — Kafka pie.market.ticks (Python option chain builder)
            kafkaTemplate.send(TOPIC, tickJson);
            log.debug("📤 TICK → {}", tickJson.substring(0, Math.min(80, tickJson.length())));

            // Step 2 — GAP-2 FIX: feed MarketStateBuilder accumulator
            // Parsed tick → builder aggregates all strikes → publishes MarketState every 2s
            try {
                Map<String, Object> tickMap = objectMapper.readValue(
                        tickJson, new TypeReference<>() {});
                marketStateBuilder.onTick(tickMap);
            } catch (Exception e) {
                log.debug("MarketStateBuilder.onTick failed (non-fatal): {}", e.getMessage());
            }

            // Step 3 — Health probe: last_tick_time for /api/system/health
            redis.opsForValue().set(
                    LAST_TICK_KEY,
                    String.valueOf(System.currentTimeMillis()),
                    Duration.ofMinutes(5)
            );

        } catch (Exception e) {
            log.error("❌ Tick publish failed: {}", e.getMessage());
        }
    }
}
