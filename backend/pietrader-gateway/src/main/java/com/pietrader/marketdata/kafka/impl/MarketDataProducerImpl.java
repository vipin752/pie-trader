package com.pietrader.marketdata.kafka.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.marketdata.dto.MarketTickDTO;
import com.pietrader.marketdata.kafka.IMarketDataProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataProducerImpl implements IMarketDataProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    private static final String TOPIC = "pie.market.ticks";

    @Override
    public void publishTick(MarketTickDTO tick) {
        try {
            String json = objectMapper.writeValueAsString(tick);
            kafkaTemplate.send(TOPIC, tick.getSymbol(), json);
            log.debug("📤 Tick published symbol={}", tick.getSymbol());
        } catch (Exception e) {
            log.error("❌ Tick publish failed: {}", e.getMessage());
        }
    }
}
