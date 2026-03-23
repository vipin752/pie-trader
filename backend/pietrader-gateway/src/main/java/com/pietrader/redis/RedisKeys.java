package com.pietrader.redis;

/**
 * Centralized Redis key registry.
 * ALL Redis keys must be defined here — no magic strings anywhere else.
 *
 * Pattern: NAMESPACE:IDENTIFIER
 */
public final class RedisKeys {

    private RedisKeys() {}

    // ── TRADE LOCK ──────────────────────────────────────────────────────────
    /** Prevents duplicate trade on same symbol+strike. TTL: 10 min */
    public static String tradeLock(String symbol) {
        return "trade_lock:" + symbol;
    }

    // ── POSITION ────────────────────────────────────────────────────────────
    /** Active position JSON for a symbol. TTL: 1 day */
    public static String position(String symbol) {
        return "position:" + symbol;
    }

    // ── TRADE STATE ─────────────────────────────────────────────────────────
    /** Full TradeState JSON for a symbol */
    public static String tradeState(String symbol) {
        return "trade_state:" + symbol;
    }

    // ── TRADE PHASE (STATE MACHINE) ─────────────────────────────────────────
    /**
     * Current TradePhase enum name for a symbol.
     * Values: NO_TRADE | PREPARE | READY | EXECUTE | MANAGE | EXIT
     * Set by TradeStateMachine, read by BreakoutTriggerEngine + health check.
     * Key: trade:phase:NIFTY
     */
    public static String tradePhase(String symbol) {
        return "trade:phase:" + symbol;
    }

    // ── DAILY COUNTERS ──────────────────────────────────────────────────────
    /** How many trades placed today for a symbol. TTL: end of day */
    public static String dailyTradeCount(String symbol) {
        return "daily_trade_count:" + symbol;
    }

    /** Cumulative PnL today for a symbol (in rupees) */
    public static String dailyPnl(String symbol) {
        return "daily_pnl:" + symbol;
    }

    /** Max loss breach flag */
    public static String maxLossBreach(String symbol) {
        return "max_loss_breach:" + symbol;
    }

    // ── COOLDOWN ────────────────────────────────────────────────────────────
    /** Cooldown after a trade — prevents re-entry immediately. TTL: configurable */
    public static String cooldown(String symbol) {
        return "cooldown:" + symbol;
    }

    // ── LAST TRADE ──────────────────────────────────────────────────────────
    /** Epoch millis of last trade */
    public static String lastTradeTime(String symbol) {
        return "last_trade_time:" + symbol;
    }

    // ── SIGNAL CACHE ────────────────────────────────────────────────────────
    /** Latest analytics signal JSON for a symbol. TTL: 30 min */
    public static String latestSignal(String symbol) {
        return "latest_signal:" + symbol;
    }
}
