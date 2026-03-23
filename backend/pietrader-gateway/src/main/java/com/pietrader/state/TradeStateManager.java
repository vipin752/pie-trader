package com.pietrader.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * PIE TRADER — TradeStateManager
 * All existing methods PRESERVED (existing tests pass).
 * New methods added for TradingOrchestrator/PositionManager/DashboardController.
 *
 * FIX: cacheSignal TTL changed from 5 minutes → 30 minutes.
 * 5 min was too short — the signal expired between Kafka messages causing
 * /api/trade-card to return NO_SIGNAL even when Python was healthy.
 * Python pushes every 2s during market hours, but at startup / market close
 * there may be no new messages for > 5 min. 30 min covers pre-market checks,
 * lunch breaks, and the startup period before Java restarts Kafka consumption.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeStateManager {

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    @Value("${trading.capital:200000}")
    private double defaultCapital;

    // ─────────────────────────────────────────────────────────────────────────
    // EXISTING METHODS — DO NOT MODIFY (existing tests depend on these)
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isLocked(String symbol) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.tradeLock(symbol)));
    }
    public void lock(String symbol, Duration duration) {
        redis.opsForValue().set(RedisKeys.tradeLock(symbol), "1", duration);
        log.info("🔒 Trade locked: {} for {}s", symbol, duration.getSeconds());
    }
    public void unlock(String symbol) {
        redis.delete(RedisKeys.tradeLock(symbol));
        log.info("🔓 Trade unlocked: {}", symbol);
    }
    public boolean isTradeExecuted(String key) { return isLocked(key); }
    public void markTradeExecuted(String key)  { lock(key, Duration.ofMinutes(10)); }

    public void savePosition(String symbol, TradeState state) {
        try {
            redis.opsForValue().set(RedisKeys.position(symbol), objectMapper.writeValueAsString(state));
            log.info("📍 Position saved: {} → {}", symbol, state.getStrike());
        } catch (Exception e) { log.error("Failed to save position for {}", symbol, e); }
    }
    public TradeState getPosition(String symbol) {
        try {
            String json = redis.opsForValue().get(RedisKeys.position(symbol));
            return json == null ? null : objectMapper.readValue(json, TradeState.class);
        } catch (Exception e) { return null; }
    }
    public boolean hasActivePosition(String symbol) {
        TradeState s = getPosition(symbol); return s != null && s.isTradeActive();
    }
    public void clearPosition(String symbol) {
        redis.delete(RedisKeys.position(symbol));
        log.info("🗑️ Position cleared: {}", symbol);
    }

    public long incrementTradeCount(String symbol) {
        Long count = redis.opsForValue().increment(RedisKeys.dailyTradeCount(symbol));
        redis.expire(RedisKeys.dailyTradeCount(symbol), Duration.ofHours(24));
        return count != null ? count : 0L;
    }
    public long getDailyTradeCount(String symbol) {
        String val = redis.opsForValue().get(RedisKeys.dailyTradeCount(symbol));
        return val != null ? Long.parseLong(val) : 0L;
    }

    public void updateDailyPnl(String symbol, double pnl) {
        String key     = RedisKeys.dailyPnl(symbol);
        String current = redis.opsForValue().get(key);
        double updated = (current != null ? Double.parseDouble(current) : 0.0) + pnl;
        redis.opsForValue().set(key, String.valueOf(updated), Duration.ofHours(24));
        if (updated < 0) {
            double lossPct = Math.abs(updated) / getCapital() * 100.0;
            redis.opsForValue().set("current_loss_pct:" + symbol,
                    String.format("%.4f", lossPct), Duration.ofHours(24));
        }
        log.info("💰 Daily PnL for {}: ₹{}", symbol, updated);
    }
    public double getDailyPnl(String symbol) {
        String val = redis.opsForValue().get(RedisKeys.dailyPnl(symbol));
        return val != null ? Double.parseDouble(val) : 0.0;
    }

    public void setMaxLossBreach(String symbol) {
        redis.opsForValue().set(RedisKeys.maxLossBreach(symbol), "1", Duration.ofHours(24));
        log.warn("🚨 MAX LOSS BREACH: {}", symbol);
    }
    public boolean isMaxLossBreached(String symbol) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.maxLossBreach(symbol)));
    }

    public void setCooldown(String symbol, Duration duration) {
        redis.opsForValue().set(RedisKeys.cooldown(symbol), "1", duration);
        log.info("⏳ Cooldown for {} — {}s", symbol, duration.getSeconds());
    }
    public boolean isInCooldown(String symbol) {
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.cooldown(symbol)));
    }

    public void setLastTradeTime(String symbol) {
        redis.opsForValue().set(RedisKeys.lastTradeTime(symbol),
                String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));
    }
    public Long getLastTradeTime(String symbol) {
        String val = redis.opsForValue().get(RedisKeys.lastTradeTime(symbol));
        return val != null ? Long.parseLong(val) : null;
    }

    // FIX: 5 min → 30 min — prevents NO_SIGNAL at startup and market close gaps
    public void cacheSignal(String symbol, String signalJson) {
        redis.opsForValue().set(RedisKeys.latestSignal(symbol), signalJson, Duration.ofMinutes(30));
    }
    public String getCachedSignal(String symbol) {
        return redis.opsForValue().get(RedisKeys.latestSignal(symbol));
    }

    public void resetDailyState(String symbol) {
        redis.delete(RedisKeys.dailyTradeCount(symbol));
        redis.delete(RedisKeys.dailyPnl(symbol));
        redis.delete(RedisKeys.maxLossBreach(symbol));
        redis.delete(RedisKeys.cooldown(symbol));
        redis.delete(RedisKeys.tradeLock(symbol));
        redis.delete("current_loss_pct:" + symbol);
        log.info("♻️ Daily state reset for {}", symbol);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW METHODS — required by DashboardController, PositionManager, etc.
    // ─────────────────────────────────────────────────────────────────────────

    public void setMode(String mode) {
        redis.opsForValue().set("trade:mode", mode);
    }
    public String getMode() {
        String v = redis.opsForValue().get("trade:mode");
        return v != null ? v : "PAPER";
    }

    public void setCapital(double amount) {
        redis.opsForValue().set("trade:capital", String.valueOf(amount));
    }
    public double getCapital() {
        String v = redis.opsForValue().get("trade:capital");
        return v != null ? Double.parseDouble(v) : defaultCapital;
    }

    public int getMaxTrades() {
        String v = redis.opsForValue().get("trade:maxTrades");
        return v != null ? Integer.parseInt(v) : 3;
    }
    public void setMaxTrades(int n) {
        redis.opsForValue().set("trade:maxTrades", String.valueOf(n));
    }

    public double getCurrentLossPercent(String symbol) {
        String v = redis.opsForValue().get("current_loss_pct:" + symbol);
        return v != null ? Double.parseDouble(v) : 0.0;
    }
}
