package com.pietrader.marketdata.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.marketdata.dto.MarketTickDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketDataKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "pie.market.ticks";

    public void publishTick(MarketTickDTO tick) {
	try {
	    String json = objectMapper.writeValueAsString(tick);
	    kafkaTemplate.send(TOPIC, tick.getSymbol(), json);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}

