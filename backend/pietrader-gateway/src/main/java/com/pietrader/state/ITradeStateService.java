package com.pietrader.state;

import java.time.Duration;

/** All Redis state operations for trading engine. Impl: TradeStateManagerImpl */
public interface ITradeStateService {
    boolean    isLocked(String symbol);
    void       lock(String symbol, Duration duration);
    void       unlock(String symbol);
    void       savePosition(String symbol, TradeState state);
    TradeState getPosition(String symbol);
    boolean    hasActivePosition(String symbol);
    void       clearPosition(String symbol);
    long       incrementTradeCount(String symbol);
    long       getDailyTradeCount(String symbol);
    void       updateDailyPnl(String symbol, double pnl);
    double     getDailyPnl(String symbol);
    void       setMaxLossBreach(String symbol);
    boolean    isMaxLossBreached(String symbol);
    void       setCooldown(String symbol, Duration duration);
    boolean    isInCooldown(String symbol);
    void       setLastTradeTime(String symbol);
    Long       getLastTradeTime(String symbol);
    void       cacheSignal(String symbol, String signalJson);
    String     getCachedSignal(String symbol);
    void       resetDailyState(String symbol);
    void       setMode(String mode);
    String     getMode();
    void       setCapital(double amount);
    double     getCapital();
    int        getMaxTrades();
    void       setMaxTrades(int n);
    double     getCurrentLossPercent(String symbol);
}
