package com.pietrader.risk;

/** 6-rule risk engine. Impl: RiskManagerImpl */
public interface IRiskService {
    RiskCheckResult check(String symbol, int confidenceScore);
    double          getSizingMultiplier(String symbol);
    void            onTradeExecuted(String symbol);
    void            onTradeClosed(String symbol, double pnl);
    void            resetDailyLossStreak(String symbol);
}
