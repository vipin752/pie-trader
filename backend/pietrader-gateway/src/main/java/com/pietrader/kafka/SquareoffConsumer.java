package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.execution.PositionManager;
import com.pietrader.state.TradeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * PIE TRADER — SquareoffConsumer
 *
 * Consumes: pie.squareoff.signals
 * Published by: Python SquareoffPublisher (emergency/risk exit)
 *
 * Contract message format:
 * {
 *   "symbol":    String  — NIFTY / BANKNIFTY / ALL
 *   "action":    String  — SQUAREOFF
 *   "reason":    String  — RISK_BREACH / MAX_LOSS / MANUAL / GAMMA_FLIP
 *   "timestamp": Long    — epoch millis
 * }
 *
 * GAP-6 FIX: Previously closeSymbol() passed pnl=0.0 hardcoded.
 * Now computes actual PnL from position entry price vs current price.
 * Without this, every squareoff-triggered closure recorded ₹0 PnL
 * in journal and daily state, corrupting risk calculations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SquareoffConsumer {

    private final PositionManager positionManager;
    private final AlertProducer   alertProducer;
    private final ObjectMapper    objectMapper;

    @KafkaListener(topics = "pie.squareoff.signals", groupId = "pietrader-squareoff")
    public void consume(String message) {
        log.warn("⚡ SQUAREOFF SIGNAL received: {}", message);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);

            String symbol = String.valueOf(payload.getOrDefault("symbol", "")).toUpperCase();
            String action = String.valueOf(payload.getOrDefault("action", "")).toUpperCase();
            String reason = String.valueOf(payload.getOrDefault("reason", "SQUAREOFF_SIGNAL"));

            if (!"SQUAREOFF".equals(action)) {
                log.warn("⚠️ Squareoff signal with unknown action={} — ignored", action);
                return;
            }

            if ("ALL".equals(symbol)) {
                log.warn("🚨 SQUAREOFF ALL — closing all open positions");
                for (String s : positionManager.getActiveSymbols()) {
                    closeSymbol(s, reason);
                }
                alertProducer.critical("SQUAREOFF_ALL", "Emergency squareoff: " + reason);
            } else {
                closeSymbol(symbol, reason);
                alertProducer.warn("SQUAREOFF_SIGNAL", symbol + " force-closed: " + reason);
            }

        } catch (Exception e) {
            log.error("❌ SquareoffConsumer error: {}", e.getMessage(), e);
        }
    }

    private void closeSymbol(String symbol, String reason) {
        try {
            if (!positionManager.hasActivePosition(symbol)) {
                log.info("ℹ️ No active position for {} — skipping squareoff", symbol);
                return;
            }

            TradeState state = positionManager.getPosition(symbol);

            // GAP-6 FIX: Compute real PnL from position state
            // Old code: positionManager.closePosition(symbol, ltp, reason, 0.0)  ← wrong
            // New code: compute pnl from entry vs current price
            double exitPrice = state != null ? state.getCurrentPrice() : 0.0;
            double pnl       = computePnl(state, exitPrice);

            positionManager.closePosition(symbol, exitPrice, "SQUAREOFF_" + reason, pnl);

            log.warn("✅ Force-closed {} reason={} exitPrice={} pnl=₹{}",
                    symbol, reason, exitPrice, String.format("%.2f", pnl));

        } catch (Exception e) {
            log.error("❌ Failed to squareoff {}: {}", symbol, e.getMessage(), e);
        }
    }

    /**
     * GAP-6 FIX: Compute actual PnL.
     * For BUY trades:  PnL = (exitPrice - entryPrice) * quantity
     * For SELL trades: PnL = (entryPrice - exitPrice) * quantity
     * Returns 0.0 if state is null or entry price is missing.
     */
    private double computePnl(TradeState state, double exitPrice) {
        if (state == null || state.getEntryPrice() == null || state.getEntryPrice() <= 0) {
            return 0.0;
        }
        double entryPrice = state.getEntryPrice();
        int    quantity   = state.getQuantity() > 0 ? state.getQuantity() : 75;
        String direction  = state.getDirection() != null ? state.getDirection().toUpperCase() : "BUY";

        double diff = "SELL".equalsIgnoreCase(direction)
                ? entryPrice - exitPrice
                : exitPrice  - entryPrice;

        return Math.round(diff * quantity * 100.0) / 100.0;
    }
}
