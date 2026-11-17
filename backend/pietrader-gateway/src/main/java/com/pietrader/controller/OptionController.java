package com.pietrader.controller;

import com.pietrader.client.PythonAnalyticsClient;
import com.pietrader.service.OptionService;
import com.pietrader.state.TradeStateManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PIE TRADER — OptionController
 *
 * REST interface for option analytics.
 *
 * Two data paths: 1. KAFKA (primary): Python publishes to pie.analytics.results every 2s automatically 2. HTTP (pull):
 * Java calls Python /option-summary on demand (this controller)
 *
 * Use /api/options/test-kafka to verify the full Kafka loop end-to-end.
 */
@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
@Tag(name = "Options", description = "Option analytics — HTTP pull path")
public class OptionController
{

    private final OptionService optionService;
    private final PythonAnalyticsClient pythonClient;
    private final TradeStateManager stateManager;

    /**
     * Pure read: fetch analytics from Python (or the Redis cache behind
     * it) — never triggers execution. Safe to poll repeatedly.
     */
    @GetMapping("/summary")
    @Operation(summary = "Fetch option analytics from Python (read-only, no execution)")
    public ResponseEntity<String> getSummary(@RequestParam(defaultValue = "NIFTY") String symbol)
    {
	String result = optionService.getOptionSummary(symbol);
	if(result == null)
	    return ResponseEntity.internalServerError().body("{\"error\":\"Python analytics unavailable\"}");
	return ResponseEntity.ok(result);
    }

    /**
     * CR-002: command, not a query — explicitly triggers a fetch + signal
     * persistence + execution cycle for `symbol`, still gated by
     * ExecutionGate/RiskManager exactly as the Kafka path is. This used to
     * be GET /api/options/summary itself; a financial side effect must
     * never be reachable from a GET (master prompt CR-002 / Section 9).
     */
    @PostMapping("/trigger")
    @Operation(summary = "Manually trigger a fetch + execution cycle (command, not read-only)")
    public ResponseEntity<String> triggerAnalyticsCycle(@RequestParam(defaultValue = "NIFTY") String symbol)
    {
	String result = optionService.triggerAnalyticsCycle(symbol);
	if(result == null)
	    return ResponseEntity.internalServerError().body("{\"error\":\"Python analytics unavailable\"}");
	return ResponseEntity.ok(result);
    }

    /**
     * Dashboard view: signal summary + risk state for a symbol.
     */
    @GetMapping("/dashboard/{symbol}")
    @Operation(summary = "Dashboard — signal + risk state combined")
    public ResponseEntity<Map<String, Object>> getDashboard(@PathVariable String symbol)
    {
	return ResponseEntity.ok(
		Map.of("symbol", symbol, "hasPosition", stateManager.hasActivePosition(symbol), "isLocked",
			stateManager.isLocked(symbol), "inCooldown", stateManager.isInCooldown(symbol), "maxLossBreach",
			stateManager.isMaxLossBreached(symbol), "dailyTradeCount",
			stateManager.getDailyTradeCount(symbol), "dailyPnl", stateManager.getDailyPnl(symbol),
			"hasSignal", stateManager.getCachedSignal(symbol) != null));
    }

    /**
     * Health check — verify Python engine is up.
     */
    @GetMapping("/python/health")
    @Operation(summary = "Check Python analytics engine health")
    public ResponseEntity<Map<String, Object>> pythonHealth()
    {
	boolean up = pythonClient.isHealthy();
	return ResponseEntity.ok(Map.of("pythonEngine", up ? "UP" : "DOWN", "message",
		up ? "✅ Python analytics reachable" : "❌ Python not reachable — check port 8000"));
    }

    /**
     * Integration test: force Python to run analytics AND publish to Kafka. Java AnalyticsConsumer will receive it and
     * log "📥 Kafka received". Use this to verify the full pipeline: Python → Kafka → Java execution.
     */
    @GetMapping("/test-kafka/{symbol}")
    @Operation(summary = "Force Python to publish one result to pie.analytics.results")
    public ResponseEntity<Map<String, Object>> testKafka(@PathVariable String symbol)
    {
	String result = pythonClient.testKafkaPublish(symbol.toUpperCase());
	if(result == null)
	    return ResponseEntity.internalServerError()
		    .body(Map.of("status", "FAILED", "message", "Python not reachable or error"));
	return ResponseEntity.ok(Map.of("status", "OK", "message",
		"✅ Python published to pie.analytics.results. Check Java logs for '📥 Kafka received'.", "symbol",
		symbol.toUpperCase(), "python", result));
    }
}
