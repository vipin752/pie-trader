package com.pietrader.service.impl;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.execution.IPositionManagerService;
import com.pietrader.kafka.IAlertProducer;
import com.pietrader.service.IBrokerOrderService;
import com.pietrader.service.IPaperTradeService;
import com.pietrader.service.ISquareOffService;
import com.pietrader.state.TradeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SquareOffServiceImpl implements ISquareOffService {

    private final IPositionManagerService positionManager;
    private final IBrokerOrderService     brokerOrderService;
    private final IPaperTradeService      paperTradeService;
    private final IAlertProducer          alertProducer;

    @Value("${trading.mode:PAPER}") private String tradingMode;
    @Value("${trading.lot.size:75}") private int   lotSize;

    @Override
    public OrderResponse squareOff(String symbol, String reason) {
        TradeState state = positionManager.getPosition(symbol);
        if (state == null || !state.isTradeActive()) {
            log.info("ℹ️ No active position for {}", symbol);
            return OrderResponse.builder().status("NO_POSITION").symbol(symbol).build();
        }
        String strike   = state.getStrike();
        int    quantity = state.getQuantity() > 0 ? state.getQuantity() : lotSize;
        double ltp      = state.getCurrentPrice();

        log.warn("🚪 SQUAREOFF → {} {} qty={} reason={}", symbol, strike, quantity, reason);
        OrderResponse resp = "PAPER".equalsIgnoreCase(tradingMode)
            ? paperTradeService.squareOff(symbol, strike, quantity, ltp)
            : brokerOrderService.squareOff(symbol, strike, quantity);

        if ("SUCCESS".equals(resp.getStatus()) || "PAPER".equals(resp.getStatus())) {
            double exitPrice = resp.getPrice() > 0 ? resp.getPrice() : ltp;
            double pnl       = computePnl(state, exitPrice);
            positionManager.closePosition(symbol, exitPrice, reason, pnl);
            alertProducer.warn("SQUAREOFF",
                symbol + " closed → " + reason + " pnl=₹" + String.format("%.2f", pnl));
        } else {
            alertProducer.critical("SQUAREOFF_FAILED",
                symbol + " exit FAILED: " + resp.getErrorMessage());
        }
        return resp;
    }

    @Override
    public List<OrderResponse> squareOffAll(String reason) {
        log.warn("🚨 SQUAREOFF ALL → reason={}", reason);
        List<OrderResponse> results = new ArrayList<>();
        for (String symbol : positionManager.getActiveSymbols())
            results.add(squareOff(symbol, reason));
        alertProducer.critical("SQUAREOFF_ALL", "All positions closed → " + reason);
        return results;
    }

    private double computePnl(TradeState state, double exitPrice) {
        double diff = "SELL".equalsIgnoreCase(state.getDirection())
            ? state.getEntryPrice() - exitPrice : exitPrice - state.getEntryPrice();
        int qty = state.getQuantity() > 0 ? state.getQuantity() : lotSize;
        return diff * qty;
    }
}
