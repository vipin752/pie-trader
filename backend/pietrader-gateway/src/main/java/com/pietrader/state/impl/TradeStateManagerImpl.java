package com.pietrader.state.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.redis.RedisKeys;
import com.pietrader.state.ITradeStateService;
import com.pietrader.state.TradeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeStateManagerImpl implements ITradeStateService {

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    @Value("${trading.capital:200000}")
    private double defaultCapital;

    @Override public boolean isLocked(String symbol)            { return Boolean.TRUE.equals(redis.hasKey(RedisKeys.tradeLock(symbol))); }
    @Override public void    lock(String symbol, Duration d)    { redis.opsForValue().set(RedisKeys.tradeLock(symbol), "1", d); log.info("🔒 Locked: {} for {}s", symbol, d.getSeconds()); }
    @Override public void    unlock(String symbol)              { redis.delete(RedisKeys.tradeLock(symbol)); log.info("🔓 Unlocked: {}", symbol); }

    @Override
    public void savePosition(String symbol, TradeState state) {
        try { redis.opsForValue().set(RedisKeys.position(symbol), objectMapper.writeValueAsString(state)); }
        catch (Exception e) { log.error("savePosition failed for {}", symbol, e); }
    }

    @Override
    public TradeState getPosition(String symbol) {
        try { String j = redis.opsForValue().get(RedisKeys.position(symbol)); return j == null ? null : objectMapper.readValue(j, TradeState.class); }
        catch (Exception e) { return null; }
    }

    @Override public boolean hasActivePosition(String symbol) { TradeState s = getPosition(symbol); return s != null && s.isTradeActive(); }
    @Override public void    clearPosition(String symbol)     { redis.delete(RedisKeys.position(symbol)); }

    @Override
    public long incrementTradeCount(String symbol) {
        Long c = redis.opsForValue().increment(RedisKeys.dailyTradeCount(symbol));
        redis.expire(RedisKeys.dailyTradeCount(symbol), Duration.ofHours(24));
        return c != null ? c : 0L;
    }

    @Override
    public long getDailyTradeCount(String symbol) {
        String v = redis.opsForValue().get(RedisKeys.dailyTradeCount(symbol));
        return v != null ? Long.parseLong(v) : 0L;
    }

    @Override
    public void updateDailyPnl(String symbol, double pnl) {
        String key = RedisKeys.dailyPnl(symbol);
        String cur = redis.opsForValue().get(key);
        double upd = (cur != null ? Double.parseDouble(cur) : 0.0) + pnl;
        redis.opsForValue().set(key, String.valueOf(upd), Duration.ofHours(24));
        if (upd < 0) redis.opsForValue().set("current_loss_pct:" + symbol,
            String.format("%.4f", Math.abs(upd) / getCapital() * 100.0), Duration.ofHours(24));
        log.info("💰 Daily PnL {} = ₹{}", symbol, upd);
    }

    @Override
    public double getDailyPnl(String symbol) {
        String v = redis.opsForValue().get(RedisKeys.dailyPnl(symbol));
        return v != null ? Double.parseDouble(v) : 0.0;
    }

    @Override public void    setMaxLossBreach(String symbol)     { redis.opsForValue().set(RedisKeys.maxLossBreach(symbol), "1", Duration.ofHours(24)); log.warn("🚨 MAX LOSS BREACH: {}", symbol); }
    @Override public boolean isMaxLossBreached(String symbol)    { return Boolean.TRUE.equals(redis.hasKey(RedisKeys.maxLossBreach(symbol))); }
    @Override public void    setCooldown(String symbol, Duration d) { redis.opsForValue().set(RedisKeys.cooldown(symbol), "1", d); log.info("⏳ Cooldown {} for {}s", symbol, d.getSeconds()); }
    @Override public boolean isInCooldown(String symbol)         { return Boolean.TRUE.equals(redis.hasKey(RedisKeys.cooldown(symbol))); }

    @Override
    public void setLastTradeTime(String symbol) {
        redis.opsForValue().set(RedisKeys.lastTradeTime(symbol),
            String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));
    }

    @Override
    public Long getLastTradeTime(String symbol) {
        String v = redis.opsForValue().get(RedisKeys.lastTradeTime(symbol));
        return v != null ? Long.parseLong(v) : null;
    }

    @Override public void   cacheSignal(String symbol, String json) { redis.opsForValue().set(RedisKeys.latestSignal(symbol), json, Duration.ofMinutes(5)); }
    @Override public String getCachedSignal(String symbol)          { return redis.opsForValue().get(RedisKeys.latestSignal(symbol)); }

    @Override
    public void resetDailyState(String symbol) {
        redis.delete(RedisKeys.dailyTradeCount(symbol));
        redis.delete(RedisKeys.dailyPnl(symbol));
        redis.delete(RedisKeys.maxLossBreach(symbol));
        redis.delete(RedisKeys.cooldown(symbol));
        redis.delete(RedisKeys.tradeLock(symbol));
        redis.delete("current_loss_pct:" + symbol);
        log.info("♻️ Daily state reset for {}", symbol);
    }

    @Override public void   setMode(String mode)    { redis.opsForValue().set("trade:mode", mode); }
    @Override public String getMode()               { String v = redis.opsForValue().get("trade:mode"); return v != null ? v : "PAPER"; }
    @Override public void   setCapital(double amt)  { redis.opsForValue().set("trade:capital", String.valueOf(amt)); }
    @Override public double getCapital()            { String v = redis.opsForValue().get("trade:capital"); return v != null ? Double.parseDouble(v) : defaultCapital; }
    @Override public int    getMaxTrades()          { String v = redis.opsForValue().get("trade:maxTrades"); return v != null ? Integer.parseInt(v) : 3; }
    @Override public void   setMaxTrades(int n)     { redis.opsForValue().set("trade:maxTrades", String.valueOf(n)); }
    @Override public double getCurrentLossPercent(String symbol) { String v = redis.opsForValue().get("current_loss_pct:" + symbol); return v != null ? Double.parseDouble(v) : 0.0; }
}
