package com.pietrader.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.kafka.PositionEventProducer;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PIE TRADER — PositionManager
 *
 * All existing methods preserved exactly.
 *
 * STATE MACHINE INTEGRATION:
 *   closePosition() now calls stateMachine.onPositionClosed(symbol) → NO_TRADE.
 *
 * WHY:
 *   closePosition() is the single exit point called by:
 *     - TradingOrchestrator.squareOff()
 *     - SquareOffServiceImpl.squareOff()
 *     - ExitEngine.doExit() → squareOffService.squareOff() → closePosition()
 *   Hooking here guarantees state resets to NO_TRADE from ALL exit paths,
 *   without modifying any of those callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionManager {

    private final TradeStateManager     stateManager;
    private final PositionEventProducer positionEventProducer;
    private final StringRedisTemplate   redis;
    private final ObjectMapper          objectMapper;
    private final TradeStateMachine     stateMachine;  // NEW — state machine hook

    // ── openPosition (unchanged) ──────────────────────────────────────────────

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
                trade.getSymbol(), trade.getStrike(),
                trade.getDirection(), trade.getOrderId());

        log.info("📍 Position OPENED → {} {} {} entry={} sl={} target={}",
                trade.getSymbol(), trade.getStrike(), trade.getDirection(),
                trade.getEntryPrice(), trade.getSl(), trade.getTarget());
    }

    // ── updateSl (unchanged) ─────────────────────────────────────────────────

    public void updateSl(String symbol, double newSl) {
        TradeState state = stateManager.getPosition(symbol);
        if (state == null) return;
        state.setSl(newSl);
        state.setTrailingSl(newSl);
        state.setLastUpdateTime(System.currentTimeMillis());
        stateManager.savePosition(symbol, state);
    }

    // ── updatePnl (unchanged) ────────────────────────────────────────────────

    public void updatePnl(String symbol, double currentPrice) {
        TradeState state = stateManager.getPosition(symbol);
        if (state == null) return;
        state.setCurrentPrice(currentPrice);
        state.updatePnl();
        state.setLastUpdateTime(System.currentTimeMillis());
        stateManager.savePosition(symbol, state);
    }

    // ── closePosition — STATE MACHINE HOOK ADDED ─────────────────────────────

    public void closePosition(String symbol, double exitPrice, String exitReason, double pnl) {
        // Mark position closed in Redis
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

        // STATE MACHINE → NO_TRADE
        // Called here (not in squareOff callers) so ALL exit paths reset state:
        //   TradingOrchestrator.squareOff() → this method
        //   SquareOffServiceImpl.squareOff() → this method
        //   ExitEngine → squareOffService → SquareOffServiceImpl → this method
        try {
            stateMachine.onPositionClosed(symbol);
        } catch (Exception e) {
            log.error("❌ StateMachine reset failed for {} (non-fatal): {}", symbol, e.getMessage());
        }

        log.info("🚪 Position CLOSED → {} exitPrice={} reason={} pnl=₹{}",
                symbol, exitPrice, exitReason, String.format("%.2f", pnl));
    }

    // ── Queries (unchanged) ──────────────────────────────────────────────────

    public TradeState getPosition(String symbol) {
        return stateManager.getPosition(symbol);
    }

    public boolean hasActivePosition(String symbol) {
        return stateManager.hasActivePosition(symbol);
    }

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
        } catch (Exception e) {
            log.error("❌ Error scanning active positions: {}", e.getMessage());
        }
        return symbols;
    }
}
