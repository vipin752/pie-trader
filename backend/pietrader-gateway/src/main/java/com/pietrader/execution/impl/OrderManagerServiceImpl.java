package com.pietrader.execution.impl;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.execution.IOrderManagerService;
import com.pietrader.execution.ITradeModeService;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.service.IBrokerOrderService;
import com.pietrader.service.IPaperTradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManagerServiceImpl implements IOrderManagerService {

    private final IBrokerOrderService brokerOrderService;
    private final IPaperTradeService  paperTradeService;
    private final ITradeModeService   modeManager;

    @Value("${trading.lot.size:75}")         private int    lotSize;
    @Value("${trading.sl.percent:30.0}")     private double slPercent;
    @Value("${trading.target.percent:60.0}") private double targetPercent;

    @Override
    public Trade execute(OrderRequest request) {
        log.info("📋 OrderManager → {} {} {} lots={} mode={}",
            request.getSymbol(), request.getStrike(), request.getDirection(),
            request.getLots(), request.getMode());

        int quantity = request.getLots() * lotSize;
        OrderResponse resp = request.getMode() == TradeMode.PAPER
            ? paperTradeService.placeOrder(request.getSymbol(), request.getStrike(), request.getDirection(), quantity)
            : brokerOrderService.placeOrder(request.getSymbol(), request.getStrike(), request.getDirection(), quantity);

        return buildTrade(request, quantity, resp);
    }

    @Override
    public Trade forceExecute(String symbol, String strike, String direction) {
        return execute(OrderRequest.builder()
            .tradeId("FORCE_" + System.currentTimeMillis())
            .symbol(symbol).strike(strike).direction(direction)
            .lots(1).confidence(0).mode(modeManager.currentMode())
            .requestedAt(Instant.now().toEpochMilli()).build());
    }

    private Trade buildTrade(OrderRequest req, int quantity, OrderResponse resp) {
        if (!"SUCCESS".equals(resp.getStatus()) && !"PAPER".equals(resp.getStatus())) {
            return Trade.builder()
                .tradeId(req.getTradeId()).symbol(req.getSymbol())
                .strike(req.getStrike()).direction(req.getDirection())
                .lots(req.getLots()).quantity(quantity)
                .mode(req.getMode()).confidence(req.getConfidence())
                .entryTime(System.currentTimeMillis())
                .success(false).failureReason(resp.getErrorMessage()).build();
        }
        double entry  = resp.getPrice() > 0 ? resp.getPrice() : estimateEntry(req);
        double sl     = Math.max(1.0, entry * (1.0 - slPercent / 100.0));
        double target = entry * (1.0 + targetPercent / 100.0);
        return Trade.builder()
            .tradeId(req.getTradeId()).orderId(resp.getOrderId())
            .symbol(req.getSymbol()).strike(req.getStrike()).direction(req.getDirection())
            .lots(req.getLots()).quantity(quantity)
            .entryPrice(entry).sl(sl).target(target)
            .entryTime(System.currentTimeMillis())
            .mode(req.getMode()).confidence(req.getConfidence())
            .regime(req.getDto() != null && req.getDto().getHistoricalContext() != null
                ? req.getDto().getHistoricalContext().getRegime() : "UNKNOWN")
            .tradeType("INTRADAY").success(true)
            .strategy(req.getDto() != null && req.getDto().getTradeSignal() != null
                ? req.getDto().getTradeSignal().getStrategy() : "MOMENTUM")
            .build();
    }

    private double estimateEntry(OrderRequest req) {
        try { if (req.getDto() != null && req.getDto().getMarketContext() != null) { Double s = req.getDto().getMarketContext().getSpot(); if (s != null && s > 0) return s; } } catch (Exception ignored) {} return 0.0;
    }
}
