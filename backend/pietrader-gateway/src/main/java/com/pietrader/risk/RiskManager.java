package com.pietrader.risk;

import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PIE TRADER — RiskManager
 *
 * EXISTING method signatures PRESERVED (existing RiskManagerTest passes).
 *
 * Added (contract §8):
 *   Rule 4: 2 consecutive losses → half size (getSizingMultiplier)
 *   Rule 5: 3 consecutive losses → stop trading
 *   Rule 6: Cooldown 30 min (configurable via trading.cooldown.min)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskManager {

    private final TradeStateManager stateManager;

    @Value("${trading.max.trades:5}")          private int    maxTradesPerDay;
    @Value("${trading.max.loss:2000}")          private double maxDailyLoss;
    @Value("${trading.min.confidence:65}")      private int    minConfidence;
    @Value("${trading.capital:200000}")         private double capital;
    @Value("${trading.cooldown.min:30}")        private int    cooldownMinutes;

    /** Consecutive loss counter per symbol (in-memory) */
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveLosses =
        new ConcurrentHashMap<>();

    // ── check() — EXACT SAME SIGNATURE as before (existing tests use this) ───

    public RiskCheckResult check(String symbol, int confidenceScore) {

        // Gate 1: Max loss breach
        if (stateManager.isMaxLossBreached(symbol)) {
            return RiskCheckResult.blocked("🚨 Max daily loss breached for " + symbol);
        }

        // Gate 2: Trade count
        long count = stateManager.getDailyTradeCount(symbol);
        if (count >= maxTradesPerDay) {
            return RiskCheckResult.blocked("📊 Max trades reached: " + count + "/" + maxTradesPerDay);
        }

        // Gate 3: Active position
        if (stateManager.hasActivePosition(symbol)) {
            return RiskCheckResult.blocked("⛔ Active position exists for " + symbol);
        }

        // Gate 4: Cooldown
        if (stateManager.isInCooldown(symbol)) {
            return RiskCheckResult.blocked("⏳ In cooldown: " + symbol);
        }

        // Gate 5: Lock check
        if (stateManager.isLocked(symbol)) {
            return RiskCheckResult.blocked("🔒 Trade locked: " + symbol);
        }

        // Gate 6: Confidence threshold
        if (confidenceScore < minConfidence) {
            return RiskCheckResult.blocked("📉 Confidence too low: " + confidenceScore + " < " + minConfidence);
        }

        // Gate 7 (NEW): 3 consecutive losses → stop
        int streak = getConsecutiveLosses(symbol);
        if (streak >= 3) {
            return RiskCheckResult.blocked("🛑 3 consecutive losses — trading stopped for " + symbol);
        }

        return RiskCheckResult.allowed();
    }

    // ── New: sizing multiplier (Rule 4: 2 losses → half size) ────────────────

    public double getSizingMultiplier(String symbol) {
        int streak = getConsecutiveLosses(symbol);
        if (streak >= 2) { log.warn("⚠️ [{}] 2 losses → half size", symbol); return 0.5; }
        return 1.0;
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void onTradeExecuted(String symbol) {
        stateManager.incrementTradeCount(symbol);
        stateManager.setLastTradeTime(symbol);
        stateManager.setCooldown(symbol, Duration.ofMinutes(cooldownMinutes));
        log.info("✅ Risk state updated for {} — cooldown={}min", symbol, cooldownMinutes);
    }

    public void onTradeClosed(String symbol, double pnl) {
        stateManager.updateDailyPnl(symbol, pnl);
        if (pnl < 0) {
            int streak = consecutiveLosses.computeIfAbsent(symbol, k -> new AtomicInteger(0))
                .incrementAndGet();
            log.warn("📉 [{}] Loss streak={}", symbol, streak);
        } else {
            consecutiveLosses.computeIfAbsent(symbol, k -> new AtomicInteger(0)).set(0);
        }
        double totalPnl   = stateManager.getDailyPnl(symbol);
        double maxLossInr = capital * 0.05;
        if (totalPnl <= -maxLossInr) {
            stateManager.setMaxLossBreach(symbol);
            log.error("🚨 [{}] MAX DAILY LOSS HIT ₹{}", symbol, totalPnl);
        }
    }

    public void resetDailyLossStreak(String symbol) {
        consecutiveLosses.computeIfAbsent(symbol, k -> new AtomicInteger(0)).set(0);
    }

    private int getConsecutiveLosses(String symbol) {
        AtomicInteger c = consecutiveLosses.get(symbol);
        return c != null ? c.get() : 0;
    }
}
