package com.pietrader.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.client.PythonAnalyticsClient;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.ExecutionService;
import com.pietrader.service.OptionService;
import com.pietrader.service.TradeSignalService;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OptionService — HTTP pull path (manual trigger / scheduler).
 * Kafka path is the primary execution path.
 * This is used by REST endpoint + scheduler for polling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptionServiceImpl implements OptionService {

    private final PythonAnalyticsClient client;
    private final TradeSignalService    tradeSignalService;
    private final ExecutionService      executionService;
    private final TradeStateManager     stateManager;
    private final ObjectMapper          objectMapper;

    @Override
    public String getOptionSummary(String symbol) {
        try {
            log.info("🔍 Fetching analytics for {}", symbol);

            // Check active position first
            if (stateManager.hasActivePosition(symbol)) {
                log.info("⚠️ Trade already active for {}", symbol);
                return "{\"status\":\"TRADE_ALREADY_ACTIVE\"}";
            }

            // Fetch from Python analytics engine
            String response = client.getOptionSummary(symbol);
            if (response == null || response.isBlank()) {
                log.warn("⚠️ Empty response from analytics engine");
                return "{\"status\":\"EMPTY_RESPONSE\"}";
            }

            // Cache latest signal
            stateManager.cacheSignal(symbol, response);

            // Deserialize and process
            OptionAnalyticsDTO dto = objectMapper.readValue(response, OptionAnalyticsDTO.class);

            // Save to DB
            tradeSignalService.process(dto);

            // Run execution engine
            executionService.execute(dto);

            return response;

        } catch (Exception e) {
            log.error("❌ getOptionSummary failed for {}", symbol, e);
            return "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public OptionAnalyticsDTO getLatestSignal(String symbol) {
        try {
            String cached = stateManager.getCachedSignal(symbol);
            if (cached != null) return objectMapper.readValue(cached, OptionAnalyticsDTO.class);
        } catch (Exception e) {
            log.warn("Cache miss for {}", symbol);
        }
        return null;
    }
}
