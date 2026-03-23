package com.pietrader.kafka.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.kafka.ITradeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeEventProducerImpl implements ITradeEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    private static final String TOPIC_TRADE_EVENTS = "pie.trade.events";
    private static final String TOPIC_SQUAREOFF    = "pie.squareoff.signals";
    private static final String TOPIC_ALERTS       = "pie.alerts";

    @Override
    public void publishTradeEvent(Object payload) {
        try {
            kafkaTemplate.send(TOPIC_TRADE_EVENTS, objectMapper.writeValueAsString(payload));
            log.info("📤 Trade event published");
        } catch (Exception e) {
            log.error("❌ Trade event publish failed: {}", e.getMessage());
        }
    }

    @Override
    public void publishSquareOff(String symbol) {
        kafkaTemplate.send(TOPIC_SQUAREOFF, symbol);
        log.warn("📤 SquareOff signal published for {}", symbol);
    }

    @Override
    public void publishAlert(String message) {
        kafkaTemplate.send(TOPIC_ALERTS, message);
    }

    @Override
    public void publish(String message) {
        kafkaTemplate.send(TOPIC_TRADE_EVENTS, message);
    }
}
