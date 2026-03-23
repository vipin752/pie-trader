package com.pietrader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.ModeManager;
import com.pietrader.execution.OrderManager;
import com.pietrader.execution.PositionManager;
import com.pietrader.journal.TradeJournalService;
import com.pietrader.journal.entity.TradeJournalEntity;
import com.pietrader.mapper.TradeCardMapper;
import com.pietrader.service.OptionService;
import com.pietrader.service.SquareOffService;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import com.pietrader.stats.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * PIE TRADER — DashboardController
 *
 * All 9 dashboard endpoints.
 *
 * FIX: /api/trade-card now:
 *   1. Reads cached signal JSON from Redis (put there by AnalyticsConsumer)
 *   2. Deserializes to OptionAnalyticsDTO
 *   3. Maps to clean TradeCard via TradeCardMapper
 *   Returns structured card instead of raw 50KB JSON blob.
 *
 * All other endpoints preserved exactly — zero regression.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard", description = "PIE Trader dashboard endpoints")
public class DashboardController {

    private final PositionManager     positionManager;
    private final TradeStateManager   stateManager;
    private final ModeManager         modeManager;
    private final OrderManager        orderManager;
    private final SquareOffService    squareOffService;
    private final TradeJournalService journalService;
    private final StatsService        statsService;
    private final TradeCardMapper     tradeCardMapper;
    private final ObjectMapper        objectMapper;
    private final OptionService       optionService;  // FIX: HTTP fallback when cache empty

    private static final List<String> SYMBOLS =
            List.of("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY");

    // ─────────────────────────────────────────────────────────────────────────
    // DASHBOARD (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "System dashboard — all symbols, mode, active positions")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tradingMode",     modeManager.currentMode().name());
        res.put("systemStatus",    "RUNNING");
        res.put("timestamp",       System.currentTimeMillis());
        res.put("activePositions", positionManager.getActiveSymbols().size());
        List<Map<String, Object>> syms = new ArrayList<>();
        for (String s : SYMBOLS) syms.add(symSummary(s));
        res.put("symbols", syms);
        return ResponseEntity.ok(res);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRADE CARD — returns clean mapped card; HTTP-pulls Python if cache empty
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Three-layer resolution:
     *   1. Redis cache (latest_signal:NIFTY) — populated by AnalyticsConsumer
     *   2. HTTP pull from Python engine      — fallback when cache empty/expired
     *   3. NO_SIGNAL                         — only when Python is also unreachable
     *
     * Why this matters:
     *   - Redis TTL is 5 min → cache expires between Kafka messages at night
     *   - Consumer group offsets committed → new deployment doesn't replay old msgs
     *   - Market closed → Python may not be pushing to Kafka
     *   → Without HTTP fallback this endpoint always returns NO_SIGNAL at startup
     */
    @GetMapping("/trade-card")
    @Operation(summary = "Trade card — clean decision summary for trader UI")
    public ResponseEntity<Map<String, Object>> tradeCard(
            @RequestParam(defaultValue = "NIFTY") String symbol) {

        String sym = symbol.toUpperCase();

        // Layer 1: Redis cache (fast path — set by AnalyticsConsumer every Kafka msg)
        String cached = stateManager.getCachedSignal(sym);

        // Layer 2: HTTP fallback — call Python directly if cache is empty/expired
        if (cached == null || cached.isBlank()) {
            log.info("📡 Cache empty for {} — pulling from Python directly", sym);
            try {
                String fresh = optionService.getOptionSummary(sym);
                if (fresh != null && !fresh.isBlank()
                        && !fresh.contains("\"error\"")
                        && !fresh.contains("EMPTY_RESPONSE")) {
                    cached = fresh;
                    // Persist to cache so next hit is fast (30 min TTL via cacheSignal)
                    stateManager.cacheSignal(sym, cached);
                    log.info("✅ Python HTTP pull successful for {}", sym);
                }
            } catch (Exception e) {
                log.warn("⚠️ Python HTTP pull failed for {}: {}", sym, e.getMessage());
            }
        }

        // Layer 3: both sources unavailable
        if (cached == null || cached.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "symbol",    sym,
                    "status",    "NO_SIGNAL",
                    "message",   "No signal available. Check: 1) Python engine running at :8000 "
                            + "2) Kafka pie.analytics.results has messages "
                            + "3) GET /api/options/python/health",
                    "timestamp", System.currentTimeMillis()
            ));
        }

        // Map raw JSON → clean trade card
        try {
            OptionAnalyticsDTO dto = objectMapper.readValue(cached, OptionAnalyticsDTO.class);
            Map<String, Object> card = tradeCardMapper.toTradeCard(dto);
            log.debug("📋 Trade card served for {} action={}", sym, card.get("action"));
            return ResponseEntity.ok(card);
        } catch (Exception e) {
            log.error("❌ Trade card mapping failed for {}: {}", sym, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "symbol",     sym,
                    "status",     "PARSE_ERROR",
                    "error",      e.getMessage(),
                    "raw_signal", cached.length() > 200 ? cached.substring(0, 200) + "…" : cached,
                    "timestamp",  System.currentTimeMillis()
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POSITIONS (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/positions")
    @Operation(summary = "Active positions with PnL, SL, target")
    public ResponseEntity<List<Map<String, Object>>> positions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String symbol : positionManager.getActiveSymbols()) {
            TradeState s = positionManager.getPosition(symbol);
            if (s == null) continue;
            s.updatePnl(); s.updateRR(); s.updatePositionState();
            result.add(Map.ofEntries(
                    Map.entry("symbol",        s.getSymbol()),
                    Map.entry("entryPrice",    s.getEntryPrice()),
                    Map.entry("currentPrice",  s.getCurrentPrice()),
                    Map.entry("stopLoss",      s.getSl()),
                    Map.entry("target",        s.getTarget()),
                    Map.entry("trailingStop",  s.getTrailingStop()),
                    Map.entry("pnl",           s.getPnl()),
                    Map.entry("rrAchieved",    s.getRrAchieved()),
                    Map.entry("positionState", s.getPositionState() != null ? s.getPositionState() : "OPEN"),
                    Map.entry("strike",        s.getStrike()    != null ? s.getStrike()    : ""),
                    Map.entry("direction",     s.getDirection() != null ? s.getDirection() : ""),
                    Map.entry("orderId",       s.getOrderId()   != null ? s.getOrderId()   : ""),
                    Map.entry("entryTime",     s.getEntryTime())
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RISK (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/risk")
    @Operation(summary = "Risk state per symbol — PnL, loss %, trade count, locks")
    public ResponseEntity<List<Map<String, Object>>> riskState() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String s : SYMBOLS) {
            result.add(Map.ofEntries(
                    Map.entry("symbol",             s),
                    Map.entry("dailyPnl",           stateManager.getDailyPnl(s)),
                    Map.entry("dailyTradeCount",    stateManager.getDailyTradeCount(s)),
                    Map.entry("currentLossPercent", stateManager.getCurrentLossPercent(s)),
                    Map.entry("isMaxLossBreached",  stateManager.isMaxLossBreached(s)),
                    Map.entry("isLocked",           stateManager.isLocked(s)),
                    Map.entry("isInCooldown",       stateManager.isInCooldown(s)),
                    Map.entry("hasActivePosition",  positionManager.hasActivePosition(s)),
                    Map.entry("capital",            stateManager.getCapital()),
                    Map.entry("maxTrades",          stateManager.getMaxTrades()),
                    Map.entry("tradingMode",        stateManager.getMode())
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JOURNAL (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/journal")
    @Operation(summary = "Trade journal — all closed trades or open positions")
    public ResponseEntity<List<TradeJournalEntity>> journal(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "false") boolean openOnly) {
        List<TradeJournalEntity> entries;
        if (symbol != null && !symbol.isBlank()) {
            entries = journalService.getJournalForSymbol(symbol.toUpperCase());
        } else if (openOnly) {
            entries = journalService.getOpenEntries();
        } else {
            entries = journalService.getAllClosedForTraining();
        }
        return ResponseEntity.ok(entries);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATS (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Cumulative stats — win rate, avg PnL, Sharpe, drawdown")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestParam(required = false) String symbol) {
        return ResponseEntity.ok(statsService.computeStats(symbol));
    }

    @GetMapping("/stats/today")
    @Operation(summary = "Today's stats for a symbol")
    public ResponseEntity<Map<String, Object>> statsToday(
            @RequestParam(defaultValue = "NIFTY") String symbol) {
        return ResponseEntity.ok(statsService.todayStats(symbol.toUpperCase()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODE (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/mode")
    @Operation(summary = "Set trading mode: PAPER | LIVE | MANUAL")
    public ResponseEntity<Map<String, String>> setMode(@RequestBody Map<String, String> body) {
        String mode = body.getOrDefault("mode", "").toUpperCase();
        try {
            modeManager.setMode(mode);
            stateManager.setMode(mode);
            return ResponseEntity.ok(Map.of("status", "OK", "mode", modeManager.currentMode().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "Unknown mode: " + mode));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MANUAL TRADE (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/manual-trade")
    @Operation(summary = "Force-place a trade manually")
    public ResponseEntity<Map<String, Object>> manualTrade(@RequestBody Map<String, String> body) {
        String symbol    = body.getOrDefault("symbol",    "").toUpperCase();
        String strike    = body.getOrDefault("strike",    "");
        String direction = body.getOrDefault("direction", "BUY").toUpperCase();
        if (symbol.isEmpty() || strike.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "symbol and strike required"));
        com.pietrader.execution.model.Trade trade = orderManager.forceExecute(symbol, strike, direction);
        return ResponseEntity.ok(Map.of(
                "status",     trade.isSuccess() ? "OK" : "FAILED",
                "orderId",    trade.getOrderId()    != null ? trade.getOrderId()    : "",
                "symbol",     trade.getSymbol(),
                "strike",     trade.getStrike()     != null ? trade.getStrike()     : "",
                "direction",  trade.getDirection()  != null ? trade.getDirection()  : "",
                "entryPrice", trade.getEntryPrice(),
                "mode",       modeManager.currentMode().name(),
                "timestamp",  System.currentTimeMillis()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SQUAREOFF (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/squareoff")
    @Operation(summary = "Square off a symbol or all positions")
    public ResponseEntity<Map<String, Object>> squareoff(@RequestBody Map<String, String> body) {
        String symbol = body.getOrDefault("symbol", "").toUpperCase();
        if ("ALL".equals(symbol)) {
            List<com.pietrader.broker.model.OrderResponse> r = squareOffService.squareOffAll("MANUAL_UI");
            return ResponseEntity.ok(Map.of("status", "OK", "closed", r.size()));
        }
        if (!positionManager.hasActivePosition(symbol))
            return ResponseEntity.ok(Map.of("status", "NO_POSITION", "symbol", symbol));
        squareOffService.squareOff(symbol, "MANUAL_UI");
        return ResponseEntity.ok(Map.of("status", "OK", "symbol", symbol));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> symSummary(String symbol) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol",          symbol);
        m.put("dailyPnl",        stateManager.getDailyPnl(symbol));
        m.put("tradesToday",     stateManager.getDailyTradeCount(symbol));
        m.put("hasPosition",     positionManager.hasActivePosition(symbol));
        m.put("lossPercent",     stateManager.getCurrentLossPercent(symbol));
        m.put("maxLossBreached", stateManager.isMaxLossBreached(symbol));
        m.put("isLocked",        stateManager.isLocked(symbol));
        m.put("hasSignal",       stateManager.getCachedSignal(symbol) != null);
        return m;
    }
}
