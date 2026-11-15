package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_TRADE_EVENTS  = "pie.trade.events";
    private static final String TOPIC_SQUAREOFF      = "pie.squareoff.signals";
    private static final String TOPIC_ALERTS         = "pie.alerts";

    public void publishTradeEvent(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(TOPIC_TRADE_EVENTS, json);
            log.info("📤 Trade event published");
        } catch (Exception e) {
            log.error("❌ Failed to publish trade event", e);
        }
    }

    public void publishSquareOff(String symbol) {
        kafkaTemplate.send(TOPIC_SQUAREOFF, symbol);
        log.info("📤 SquareOff signal published for {}", symbol);
    }

    public void publishAlert(String message) {
        kafkaTemplate.send(TOPIC_ALERTS, message);
    }

    /** Backward compat */
    public void publish(String message) {
        kafkaTemplate.send(TOPIC_TRADE_EVENTS, message);
    }
}
