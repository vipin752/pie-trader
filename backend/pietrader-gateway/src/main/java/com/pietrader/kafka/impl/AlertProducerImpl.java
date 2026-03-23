package com.pietrader.kafka.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.kafka.IAlertProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertProducerImpl implements IAlertProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper                  objectMapper;

    @Override
    public void info(String type, String message)     { send(type, message, "INFO"); }

    @Override
    public void warn(String type, String message)     { send(type, message, "WARN"); }

    @Override
    public void critical(String type, String message) {
        send(type, message, "CRITICAL");
        log.error("🚨 CRITICAL ALERT — type={} msg={}", type, message);
    }

    private void send(String type, String message, String severity) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "type", type, "message", message,
                "severity", severity, "timestamp", System.currentTimeMillis()));
            kafkaTemplate.send("pie.alerts", type, json);
        } catch (Exception e) {
            log.error("❌ Alert publish failed: {}", e.getMessage());
        }
    }
}
