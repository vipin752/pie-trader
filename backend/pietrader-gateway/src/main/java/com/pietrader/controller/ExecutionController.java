package com.pietrader.controller;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.execution.ExecutionService;
import com.pietrader.risk.RiskManager;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PHASE 3 — Execution REST API
 * Used for manual triggers, testing, dashboard actions.
 */
@RestController
@RequestMapping("/api/execution")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Execution", description = "Trade execution endpoints")
public class ExecutionController {

    private final ExecutionService  executionService;
    private final TradeStateManager stateManager;
    private final RiskManager       riskManager;

    // ── FORCE EXECUTE ───────────────────────────────────────────────────────
    @PostMapping("/force")
    @Operation(summary = "Force execute a trade (test/manual)")
    public ResponseEntity<OrderResponse> forceExecute(
            @RequestParam String symbol,
            @RequestParam String strike,
            @RequestParam String direction) {

        log.info("🔧 Force execute API → {} {} {}", symbol, strike, direction);
        OrderResponse result = executionService.forceExecute(symbol, strike, direction);
        return ResponseEntity.ok(result);
    }

    // ── SQUARE OFF ──────────────────────────────────────────────────────────
    @PostMapping("/squareoff")
    @Operation(summary = "Square off active position")
    public ResponseEntity<OrderResponse> squareOff(@RequestParam String symbol) {
        log.info("📤 Square off API → {}", symbol);
        OrderResponse result = executionService.squareOff(symbol);
        return ResponseEntity.ok(result);
    }

    // ── POSITION STATUS ─────────────────────────────────────────────────────
    @GetMapping("/position/{symbol}")
    @Operation(summary = "Get active position for symbol")
    public ResponseEntity<TradeState> getPosition(@PathVariable String symbol) {
        TradeState state = stateManager.getPosition(symbol);
        return state != null ? ResponseEntity.ok(state) : ResponseEntity.noContent().build();
    }

    // ── RISK STATUS ─────────────────────────────────────────────────────────
    @GetMapping("/risk/{symbol}")
    @Operation(summary = "Get risk status for symbol")
    public ResponseEntity<Map<String, Object>> getRiskStatus(@PathVariable String symbol) {
        return ResponseEntity.ok(Map.of(
            "symbol",          symbol,
            "isLocked",        stateManager.isLocked(symbol),
            "hasPosition",     stateManager.hasActivePosition(symbol),
            "inCooldown",      stateManager.isInCooldown(symbol),
            "maxLossBreach",   stateManager.isMaxLossBreached(symbol),
            "dailyTradeCount", stateManager.getDailyTradeCount(symbol),
            "dailyPnl",        stateManager.getDailyPnl(symbol)
        ));
    }

    // ── UNLOCK (emergency) ──────────────────────────────────────────────────
    @PostMapping("/unlock/{symbol}")
    @Operation(summary = "Emergency unlock (use with caution)")
    public ResponseEntity<String> unlock(@PathVariable String symbol) {
        stateManager.unlock(symbol);
        stateManager.clearPosition(symbol);
        return ResponseEntity.ok("🔓 Unlocked: " + symbol);
    }

    // ── EOD RESET ───────────────────────────────────────────────────────────
    @PostMapping("/reset/{symbol}")
    @Operation(summary = "Reset daily state (EOD)")
    public ResponseEntity<String> resetDailyState(@PathVariable String symbol) {
        stateManager.resetDailyState(symbol);
        return ResponseEntity.ok("♻️ Daily state reset for: " + symbol);
    }
}
