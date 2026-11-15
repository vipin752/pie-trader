package com.pietrader.service.impl;

import com.pietrader.dto.TradeLock;
import com.pietrader.dto.risk.PositionManagementDTO;
import com.pietrader.dto.risk.RiskState;
import com.pietrader.service.IRedisService;
import com.pietrader.service.ITradeExecutionStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeExecutionStateServiceImpl implements ITradeExecutionStateService {

    private final IRedisService redis;

    @Override
    public boolean isTradeLocked(String symbol) {
        return redis.exists("trade:lock:" + symbol);
    }

    @Override
    public void lockTrade(String symbol, String strategy) {
        TradeLock lock = new TradeLock(true, strategy, System.currentTimeMillis());
        redis.set("trade:lock:" + symbol, lock, 600);
    }

    @Override
    public boolean hasActivePosition(String symbol) {
        PositionManagementDTO state = redis.get("position:" + symbol, PositionManagementDTO.class);
        return state != null && "OPEN".equals(state.getPositionStatus());
    }

    @Override
    public void savePosition(PositionManagementDTO state) {
        redis.set("position:" + state.getState(), state.getPositionStatus());
    }

    @Override
    public void closePosition(String symbol) {
        PositionManagementDTO state = redis.get("position:" + symbol, PositionManagementDTO.class);
        if (state != null) {
            state.setPositionStatus("CLOSED");
            redis.set("position:" + symbol, state);
        }
    }

    @Override
    public boolean isCooldownActive(String symbol) {
        return redis.exists("cooldown:" + symbol);
    }

    @Override
    public void setCooldown(String symbol) {
        redis.set("cooldown:" + symbol, true, 900);
    }

    @Override
    public boolean isRiskAllowed() {
        RiskState risk = redis.get("risk:daily", RiskState.class);
        if (risk == null) return true;
        return risk.getPnl() > risk.getMaxLoss();
    }
}
