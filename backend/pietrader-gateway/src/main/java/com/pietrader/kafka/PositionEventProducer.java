package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.state.TradeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes position state changes to pie.position.events.
 * Python PositionEventConsumer reads this to suppress duplicate EXECUTE signals.
 *
 * Called from:
 *   - ExecutionServiceImpl.postExecute()  → status=OPEN
 *   - ExecutionServiceImpl.squareOff()    → status=CLOSED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "pie.position.events";

    public void publishOpen(String symbol, String strike, String direction, String orderId) {
        publish(symbol, Map.of(
            "symbol",    symbol,
            "strike",    strike,
            "direction", direction,
            "orderId",   orderId,
            "status",    "OPEN",
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void publishClosed(String symbol, double realisedPnl) {
        publish(symbol, Map.of(
            "symbol",      symbol,
            "status",      "CLOSED",
            "realisedPnl", realisedPnl,
            "timestamp",   System.currentTimeMillis()
        ));
    }

    private void publish(String symbol, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC, symbol, json);
            log.info("📤 PositionEvent → {} status={}", symbol, payload.get("status"));
        } catch (Exception e) {
            log.error("❌ PositionEvent publish failed: {}", e.getMessage());
        }
    }
}
