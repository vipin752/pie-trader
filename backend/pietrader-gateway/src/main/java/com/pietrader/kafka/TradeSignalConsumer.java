package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.service.TradeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Legacy consumer — kept for backward compat.
 * Disabled when trading.mode is set (TradingOrchestrator via AnalyticsConsumer handles it).
 * Uses separate groupId so messages are not split with AnalyticsConsumer.
 */
@Component
@ConditionalOnProperty(name = "trading.legacy.consumer.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class TradeSignalConsumer {

    private final TradeSignalService service;
    private final ObjectMapper       mapper;

    @KafkaListener(topics = "pie.analytics.results", groupId = "pietrader-legacy-group")
    public void consume(String message) {
        try {
            OptionAnalyticsDTO dto = mapper.readValue(message, OptionAnalyticsDTO.class);
            service.process(dto);
        } catch (Exception e) {
            log.error("❌ Legacy consumer error: {}", e.getMessage());
        }
    }
}
