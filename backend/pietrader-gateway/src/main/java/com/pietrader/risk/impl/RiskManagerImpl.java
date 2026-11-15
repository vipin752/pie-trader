package com.pietrader.risk.impl;

import com.pietrader.risk.IRiskService;
import com.pietrader.risk.RiskCheckResult;
import com.pietrader.state.ITradeStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskManagerImpl implements IRiskService {

    private final ITradeStateService stateManager;

    @Value("${trading.max.trades:3}")      private int    maxTradesPerDay;
    @Value("${trading.max.loss:2000}")     private double maxDailyLoss;
    @Value("${trading.min.confidence:65}") private int    minConfidence;
    @Value("${trading.capital:200000}")    private double capital;
    @Value("${trading.cooldown.min:30}")   private int    cooldownMinutes;

    private final ConcurrentHashMap<String, AtomicInteger> consecutiveLosses =
        new ConcurrentHashMap<>();

    @Override
    public RiskCheckResult check(String symbol, int confidenceScore) {
        if (stateManager.isMaxLossBreached(symbol))
            return RiskCheckResult.blocked("🚨 Max daily loss breached for " + symbol);

        long count = stateManager.getDailyTradeCount(symbol);
        if (count >= maxTradesPerDay)
            return RiskCheckResult.blocked("📊 Max trades reached: " + count + "/" + maxTradesPerDay);

        if (stateManager.hasActivePosition(symbol))
            return RiskCheckResult.blocked("⛔ Active position exists for " + symbol);

        if (stateManager.isInCooldown(symbol))
            return RiskCheckResult.blocked("⏳ In cooldown: " + symbol);

        if (stateManager.isLocked(symbol))
            return RiskCheckResult.blocked("🔒 Trade locked: " + symbol);

        if (confidenceScore < minConfidence)
            return RiskCheckResult.blocked("📉 Confidence too low: " + confidenceScore);

        if (getStreak(symbol) >= 3)
            return RiskCheckResult.blocked("🛑 3 consecutive losses — stopped for " + symbol);

        return RiskCheckResult.allowed();
    }

    @Override
    public double getSizingMultiplier(String symbol) {
        if (getStreak(symbol) >= 2) { log.warn("⚠️ [{}] 2 losses → half size", symbol); return 0.5; }
        return 1.0;
    }

    @Override
    public void onTradeExecuted(String symbol) {
        stateManager.incrementTradeCount(symbol);
        stateManager.setLastTradeTime(symbol);
        stateManager.setCooldown(symbol, Duration.ofMinutes(cooldownMinutes));
        log.info("✅ Risk state updated {} cooldown={}min", symbol, cooldownMinutes);
    }

    @Override
    public void onTradeClosed(String symbol, double pnl) {
        stateManager.updateDailyPnl(symbol, pnl);
        if (pnl < 0) {
            int streak = consecutiveLosses
                .computeIfAbsent(symbol, k -> new AtomicInteger(0)).incrementAndGet();
            log.warn("📉 [{}] Loss streak={}", symbol, streak);
        } else {
            consecutiveLosses.computeIfAbsent(symbol, k -> new AtomicInteger(0)).set(0);
        }
        if (stateManager.getDailyPnl(symbol) <= -(capital * 0.05)) {
            stateManager.setMaxLossBreach(symbol);
            log.error("🚨 [{}] MAX DAILY LOSS HIT", symbol);
        }
    }

    @Override
    public void resetDailyLossStreak(String symbol) {
        consecutiveLosses.computeIfAbsent(symbol, k -> new AtomicInteger(0)).set(0);
    }

    private int getStreak(String symbol) {
        AtomicInteger c = consecutiveLosses.get(symbol);
        return c != null ? c.get() : 0;
    }
}
