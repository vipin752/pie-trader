package com.pietrader.service;

import com.pietrader.dto.TradeLock;
import com.pietrader.dto.risk.PositionManagementDTO;
import com.pietrader.dto.risk.PositionPlanDTO;
import com.pietrader.dto.risk.RiskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeExecutionStateService {

    private final RedisService redis;

    // 🔐 TRADE LOCK
    public boolean isTradeLocked(String symbol) {
	return redis.exists("trade:lock:" + symbol);
    }

    public void lockTrade(String symbol, String strategy) {
	TradeLock lock = new TradeLock(true, strategy, System.currentTimeMillis());
	redis.set("trade:lock:" + symbol, lock, 600); // 10 min
    }

    // 📊 POSITION
    public boolean hasActivePosition(String symbol) {
	PositionManagementDTO state = redis.get("position:" + symbol, PositionManagementDTO.class);
	return state != null && "OPEN".equals(state.getPositionStatus());
    }

    public void savePosition(PositionManagementDTO state) {
	redis.set("position:" + state.getState(), state.getPositionStatus());
    }

    public void closePosition(String symbol) {
	PositionManagementDTO state = redis.get("position:" + symbol, PositionManagementDTO.class);
	if (state != null) {
	    state.setPositionStatus("CLOSED");
	    redis.set("position:" + symbol, state);
	}
    }

    // ⏳ COOLDOWN
    public boolean isCooldownActive(String symbol) {
	return redis.exists("cooldown:" + symbol);
    }

    public void setCooldown(String symbol) {
	redis.set("cooldown:" + symbol, true, 900); // 15 min
    }

    // 📉 RISK
    public boolean isRiskAllowed() {
	RiskState risk = redis.get("risk:daily", RiskState.class);

	if (risk == null) return true;

	return risk.getPnl() > risk.getMaxLoss();
    }
}

