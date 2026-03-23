package com.pietrader.controller;

import com.pietrader.service.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PIE TRADER — SystemHealthController
 *
 * Production health endpoint per the architecture checklist:
 *
 *   GET /api/system/health
 *   Returns:
 *   {
 *     "status":             "UP" | "DEGRADED",
 *     "broker_ws":          { "status": "UP", "ws_connected": true, ... },
 *     "kafka":              { "status": "UP", "topics_found": [...] },
 *     "python_engine":      { "status": "UP", "decisions_flowing": true },
 *     "java_execution":     { "status": "UP", "trading_mode": "PAPER" },
 *     "redis":              { "status": "UP", "state": {...} },
 *     "postgres":           { "status": "UP" },
 *     "ui_ws":              { "status": "UP" },
 *     "last_tick_time":     "09:31:02",
 *     "last_decision_time": "09:31:03",
 *     "last_trade_time":    "09:45:10",
 *     "execution_gate":     { "NIFTY": { "gate_state": "READY", ... } }
 *   }
 *
 * HTTP response codes:
 *   200 — all components UP
 *   503 — any critical component DOWN (broker_ws, kafka, python, redis, postgres)
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Health", description = "Full system health and pipeline status")
public class SystemHealthController {

    private final SystemHealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "Full system health — all 7 components + execution gate state")
    public ResponseEntity<Map<String, Object>> health() {
	Map<String, Object> health = healthService.getHealth();
	boolean ok = Boolean.TRUE.equals(health.get("healthy"));
	log.debug("🏥 Health check → {}", health.get("status"));
	return ok
		? ResponseEntity.ok(health)
		: ResponseEntity.status(503).body(health);
    }

    @GetMapping("/health/broker")
    @Operation(summary = "Broker WebSocket status only")
    public ResponseEntity<Map<String, Object>> brokerHealth() {
	Map<String, Object> full = healthService.getHealth();
	@SuppressWarnings("unchecked")
	Map<String, Object> broker = (Map<String, Object>) full.get("broker_ws");
	return ResponseEntity.ok(broker != null ? broker : Map.of("status", "UNKNOWN"));
    }

    @GetMapping("/health/execution-gate")
    @Operation(summary = "ExecutionGate state per symbol — why trades are or aren't firing")
    public ResponseEntity<Map<String, Object>> executionGate() {
	Map<String, Object> full = healthService.getHealth();
	@SuppressWarnings("unchecked")
	Map<String, Object> gate = (Map<String, Object>) full.get("execution_gate");
	return ResponseEntity.ok(gate != null ? gate : Map.of("status", "UNKNOWN"));
    }

    @GetMapping("/pipeline")
    @Operation(summary = "End-to-end pipeline timing — last tick, last decision, last trade")
    public ResponseEntity<Map<String, Object>> pipeline() {
	Map<String, Object> full = healthService.getHealth();
	// Map.of() is limited to 10 pairs — use Map.ofEntries() for 11+
	return ResponseEntity.ok(Map.ofEntries(
		Map.entry("last_tick_time",     full.getOrDefault("last_tick_time",     "—")),
		Map.entry("last_tick_age_sec",  full.getOrDefault("last_tick_age_sec",  -1)),
		Map.entry("last_decision_time", full.getOrDefault("last_decision_time", "—")),
		Map.entry("last_trade_time",    full.getOrDefault("last_trade_time",    "—")),
		Map.entry("active_positions",   full.getOrDefault("active_positions",   0)),
		Map.entry("trading_mode",       full.getOrDefault("trading_mode",       "PAPER")),
		Map.entry("broker_ws",          statusOf(full, "broker_ws")),
		Map.entry("python_engine",      statusOf(full, "python_engine")),
		Map.entry("kafka",              statusOf(full, "kafka")),
		Map.entry("redis",              statusOf(full, "redis")),
		Map.entry("postgres",           statusOf(full, "postgres"))
	));
    }

    @SuppressWarnings("unchecked")
    private String statusOf(Map<String, Object> full, String key) {
	Object val = full.getOrDefault(key, Map.of());
	if (val instanceof Map) {
	    Object status = ((Map<?, ?>) val).get("status");
	    return status != null ? status.toString() : "UNKNOWN";
	}
	return "UNKNOWN";
    }
}
