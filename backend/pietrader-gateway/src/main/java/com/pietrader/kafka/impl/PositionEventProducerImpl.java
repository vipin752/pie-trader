package com.pietrader.kafka.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.kafka.IPositionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionEventProducerImpl implements IPositionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    private static final String TOPIC = "pie.position.events";

    @Override
    public void publishOpen(String symbol, String strike, String direction, String orderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("symbol",    symbol);
        payload.put("strike",    strike);
        payload.put("direction", direction);
        payload.put("orderId",   orderId);
        payload.put("status",    "OPEN");
        payload.put("timestamp", System.currentTimeMillis());
        publish(symbol, payload);
    }

    @Override
    public void publishClosed(String symbol, double realisedPnl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("symbol",      symbol);
        payload.put("status",      "CLOSED");
        payload.put("realisedPnl", realisedPnl);
        payload.put("timestamp",   System.currentTimeMillis());
        publish(symbol, payload);
    }

    private void publish(String symbol, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(TOPIC, symbol, objectMapper.writeValueAsString(payload));
            log.info("📤 PositionEvent {} status={}", symbol, payload.get("status"));
        } catch (Exception e) {
            log.error("❌ PositionEvent publish failed: {}", e.getMessage());
        }
    }
}
