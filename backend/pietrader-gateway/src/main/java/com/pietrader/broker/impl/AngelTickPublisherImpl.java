package com.pietrader.broker.impl;

import com.pietrader.broker.angel.IAngelTickPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AngelTickPublisherImpl implements IAngelTickPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "pie.market.ticks";

    @Override
    public void publish(String tickJson) {
        try {
            kafkaTemplate.send(TOPIC, tickJson);
            log.debug("📤 Tick published: {}", tickJson.substring(0, Math.min(80, tickJson.length())));
        } catch (Exception e) {
            log.error("❌ Tick publish failed: {}", e.getMessage());
        }
    }
}
