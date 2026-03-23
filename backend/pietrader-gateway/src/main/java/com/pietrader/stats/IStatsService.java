package com.pietrader.stats;

import java.util.Map;

/** 11-metric performance stats engine. Impl: StatsServiceImpl */
public interface IStatsService {
    Map<String, Object> computeStats(String symbol);
    Map<String, Object> todayStats(String symbol);
    void                onTradeClosed(String symbol, double pnl, String exitReason);
}
