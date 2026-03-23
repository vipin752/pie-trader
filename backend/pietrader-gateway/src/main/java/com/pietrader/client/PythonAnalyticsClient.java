package com.pietrader.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

/**
 * PIE TRADER — PythonAnalyticsClient
 *
 * Calls Python analytics engine via HTTP (REST pull path).
 * Primary path is Kafka (Python publishes to pie.analytics.results automatically).
 * This HTTP path is used by:
 *   - /api/options/summary (manual trigger / testing)
 *   - TradingScheduler for pre-market fetch
 *
 * Python endpoint: GET /option-summary?symbol=NIFTY  (analytics2 app.py)
 * Python base URL: http://localhost:8000 (PYTHON_ANALYTICS_URL in application.properties)
 */
@Service
@Slf4j
public class PythonAnalyticsClient {

    @Value("${PYTHON_ANALYTICS_URL:http://localhost:8000}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetch full OptionAnalyticsDTO JSON from Python engine.
     * Python auto-selects LIVE (Angel ticks) or NSE fallback.
     */
    public String getOptionSummary(String symbol) {
	String url = baseUrl + "/option-summary?symbol=" + symbol.toUpperCase();
	log.info("📡 Calling Python analytics: {}", url);
	try {
	    String result = restTemplate.getForObject(url, String.class);
	    log.info("✅ Python response received for {} ({} chars)",
		    symbol, result != null ? result.length() : 0);
	    return result;
	} catch (Exception e) {
	    log.error("❌ Python analytics call failed for {}: {}", symbol, e.getMessage());
	    return null;
	}
    }

    /**
     * Health check — returns true if Python engine is reachable.
     */
    public boolean isHealthy() {
	try {
	    String response = restTemplate.getForObject(baseUrl + "/health", String.class);
	    return response != null && response.contains("UP");
	} catch (Exception e) {
	    return false;
	}
    }

    /**
     * Force-publish a result to Kafka from Python (for integration testing).
     * Python runs analytics then publishes to pie.analytics.results.
     */
    public String testKafkaPublish(String symbol) {
	String url = baseUrl + "/test-kafka?symbol=" + symbol.toUpperCase();
	log.info("🧪 Triggering Python Kafka test: {}", url);
	try {
	    return restTemplate.getForObject(url, String.class);
	} catch (Exception e) {
	    log.error("❌ Kafka test failed: {}", e.getMessage());
	    return null;
	}
    }
}
