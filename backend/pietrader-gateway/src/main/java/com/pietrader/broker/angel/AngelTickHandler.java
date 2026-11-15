package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles parsed tick data from binary WS frame.
 * Publishes structured MarketTickDTO JSON to pie.market.ticks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AngelTickHandler {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "pie.market.ticks";

    public void onTick(Map<String, Object> tick) {
        try {
            String symbol = (String) tick.getOrDefault("symbol", "");
            String json   = objectMapper.writeValueAsString(tick);
            kafkaTemplate.send(TOPIC, symbol, json);
            log.debug("📤 Tick → symbol={} ltp={}", symbol, tick.get("ltp"));
        } catch (Exception e) {
            log.error("❌ Tick handler error: {}", e.getMessage());
        }
    }
}
