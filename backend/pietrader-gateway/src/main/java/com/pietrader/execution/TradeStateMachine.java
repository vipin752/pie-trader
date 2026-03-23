package com.pietrader.execution;

import com.pietrader.execution.model.TradePhase;
import com.pietrader.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PIE TRADER — TradeStateMachine
 *
 * Manages trade phase per symbol in Redis.
 * Key: trade:phase:NIFTY   Value: "PREPARE" | "READY" | "EXECUTE" | ...
 * TTL: 1 day (auto-resets at midnight / next market open)
 *
 * Valid transitions:
 *   NO_TRADE  → PREPARE  (compression detected)
 *   PREPARE   → READY    (price within 0.5% of level)
 *   PREPARE   → EXECUTE  (breakout confirmed — skips READY)
 *   READY     → EXECUTE  (breakout confirmed)
 *   EXECUTE   → MANAGE   (trade placed by TradingOrchestrator)
 *   MANAGE    → EXIT     (ExitEngine triggers)
 *   EXIT      → NO_TRADE (position closed, state reset)
 *   ANY       → NO_TRADE (manual reset / EOD reset)
 *
 * Non-valid transitions are logged and rejected silently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeStateMachine {

    private final StringRedisTemplate redis;

    private static final Duration PHASE_TTL = Duration.ofDays(1);

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public TradePhase getPhase(String symbol) {
	try {
	    String v = redis.opsForValue().get(RedisKeys.tradePhase(symbol));
	    return v != null ? TradePhase.valueOf(v) : TradePhase.NO_TRADE;
	} catch (Exception e) {
	    return TradePhase.NO_TRADE;
	}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE (force-set, used by BreakoutTriggerEngine)
    // ─────────────────────────────────────────────────────────────────────────

    public void setPhase(String symbol, TradePhase phase) {
	try {
	    TradePhase prev = getPhase(symbol);
	    if (prev == phase) return;  // no change — skip write
	    redis.opsForValue().set(RedisKeys.tradePhase(symbol), phase.name(), PHASE_TTL);
	    log.info("📊 State [{}]: {} → {} ({})", symbol, prev, phase, phase.describe());
	} catch (Exception e) {
	    log.error("❌ State machine write failed for {}: {}", symbol, e.getMessage());
	}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRANSITION (validates allowed moves)
    // ─────────────────────────────────────────────────────────────────────────

    public TradePhase transition(String symbol, TradePhase requested) {
	TradePhase current = getPhase(symbol);

	// Guard: if already MANAGE/EXIT, only allow EXIT or NO_TRADE transitions
	if (current == TradePhase.MANAGE && requested == TradePhase.PREPARE) {
	    log.debug("⛔ [{}] MANAGE → PREPARE rejected (active trade in progress)", symbol);
	    return current;
	}
	if (current == TradePhase.EXECUTE && requested == TradePhase.PREPARE) {
	    log.debug("⛔ [{}] EXECUTE → PREPARE rejected (breakout in progress)", symbol);
	    return current;
	}

	setPhase(symbol, requested);
	return requested;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SPECIAL TRANSITIONS called by TradingOrchestrator / ExitEngine
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by TradingOrchestrator after successful order placement */
    public void onTradePlaced(String symbol) {
	setPhase(symbol, TradePhase.MANAGE);
	log.info("🚀 [{}] Trade placed → MANAGE", symbol);
    }

    /** Called by ExitEngine when exit is triggered */
    public void onExitTriggered(String symbol) {
	setPhase(symbol, TradePhase.EXIT);
	log.info("🚪 [{}] Exit triggered → EXIT", symbol);
    }

    /** Called by PositionManager.closePosition() after position closed */
    public void onPositionClosed(String symbol) {
	setPhase(symbol, TradePhase.NO_TRADE);
	log.info("✅ [{}] Position closed → NO_TRADE", symbol);
    }

    /** EOD reset — called by TradingScheduler */
    public void reset(String symbol) {
	redis.delete(RedisKeys.tradePhase(symbol));
	log.info("♻️ [{}] State machine reset → NO_TRADE", symbol);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATUS (for health check / dashboard)
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getStatus(String symbol) {
	TradePhase phase = getPhase(symbol);
	Map<String, Object> m = new LinkedHashMap<>();
	m.put("symbol",       symbol);
	m.put("phase",        phase.name());
	m.put("description",  phase.describe());
	m.put("actionable",   phase.isActionable());
	m.put("active_trade", phase.isActive());
	return m;
    }
}
