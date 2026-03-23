package com.pietrader.service;

import com.pietrader.dto.risk.PositionManagementDTO;

/**
 * Manages trade-lock, active-position, cooldown and risk state in Redis.
 * Impl: TradeExecutionStateServiceImpl
 */
public interface ITradeExecutionStateService {
    boolean isTradeLocked(String symbol);
    void    lockTrade(String symbol, String strategy);
    boolean hasActivePosition(String symbol);
    void    savePosition(PositionManagementDTO state);
    void    closePosition(String symbol);
    boolean isCooldownActive(String symbol);
    void    setCooldown(String symbol);
    boolean isRiskAllowed();
}
