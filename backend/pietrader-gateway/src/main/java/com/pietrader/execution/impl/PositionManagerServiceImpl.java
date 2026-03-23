package com.pietrader.execution.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.IPositionManagerService;
import com.pietrader.execution.model.Trade;
import com.pietrader.kafka.IPositionEventProducer;
import com.pietrader.state.TradeState;
import com.pietrader.state.ITradeStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionManagerServiceImpl implements IPositionManagerService {

    private final ITradeStateService     stateManager;
    private final IPositionEventProducer positionEventProducer;
    private final StringRedisTemplate    redis;
    private final ObjectMapper           objectMapper;

    @Override
    public void openPosition(Trade trade, OptionAnalyticsDTO dto) {
        if (trade == null || !trade.isSuccess()) return;
        TradeState state = new TradeState();
        state.setSymbol(trade.getSymbol());
        state.setTradeActive(true);
        state.setStrike(trade.getStrike());
        state.setDirection(trade.getDirection());
        state.setEntryPrice(trade.getEntryPrice());
        state.setSl(trade.getSl());
        state.setTarget(trade.getTarget());
        state.setQuantity(trade.getQuantity());
        state.setOrderId(trade.getOrderId());
        state.setEntryTime(trade.getEntryTime());
        state.setLastExecutionTime(System.currentTimeMillis());
        state.setLastUpdateTime(System.currentTimeMillis());
        state.setLastStrategy(trade.getStrategy());
        state.setTradeType(trade.getTradeType());
        stateManager.savePosition(trade.getSymbol(), state);
        stateManager.lock(trade.getSymbol(), Duration.ofHours(8));
        positionEventProducer.publishOpen(
            trade.getSymbol(), trade.getStrike(), trade.getDirection(), trade.getOrderId());
        log.info("📍 Position OPENED → {} {} entry={} sl={} target={}",
            trade.getSymbol(), trade.getStrike(),
            trade.getEntryPrice(), trade.getSl(), trade.getTarget());
    }

    @Override
    public void updateSl(String symbol, double newSl) {
        TradeState state = stateManager.getPosition(symbol);
        if (state == null) return;
        state.setSl(newSl);
        state.setTrailingSl(newSl);
        state.setLastUpdateTime(System.currentTimeMillis());
        stateManager.savePosition(symbol, state);
    }

    @Override
    public void updatePnl(String symbol, double currentPrice) {
        TradeState state = stateManager.getPosition(symbol);
        if (state == null) return;
        state.setCurrentPrice(currentPrice);
        state.updatePnl();
        state.setLastUpdateTime(System.currentTimeMillis());
        stateManager.savePosition(symbol, state);
    }

    @Override
    public void closePosition(String symbol, double exitPrice, String exitReason, double pnl) {
        TradeState state = stateManager.getPosition(symbol);
        if (state != null) {
            state.setTradeActive(false);
            state.setCurrentPrice(exitPrice);
            state.setLastUpdateTime(System.currentTimeMillis());
            stateManager.savePosition(symbol, state);
        }
        stateManager.clearPosition(symbol);
        stateManager.unlock(symbol);
        stateManager.updateDailyPnl(symbol, pnl);
        positionEventProducer.publishClosed(symbol, pnl);
        log.info("🚪 Position CLOSED → {} reason={} pnl=₹{:.2f}", symbol, exitReason, pnl);
    }

    @Override
    public TradeState getPosition(String symbol) { return stateManager.getPosition(symbol); }

    @Override
    public boolean hasActivePosition(String symbol) { return stateManager.hasActivePosition(symbol); }

    @Override
    public List<String> getActiveSymbols() {
        List<String> symbols = new ArrayList<>();
        try {
            Set<String> keys = redis.keys("position:*");
            if (keys != null) {
                for (String key : keys) {
                    String json = redis.opsForValue().get(key);
                    if (json != null) {
                        TradeState ts = objectMapper.readValue(json, TradeState.class);
                        if (ts.isTradeActive() && ts.getSymbol() != null)
                            symbols.add(ts.getSymbol());
                    }
                }
            }
        } catch (Exception e) { log.error("❌ getActiveSymbols error: {}", e.getMessage()); }
        return symbols;
    }
}
