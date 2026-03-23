package com.pietrader.service;

import com.pietrader.broker.BrokerAdapter;
import com.pietrader.broker.model.OrderRequest;
import com.pietrader.broker.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Live order placement via Angel SmartAPI. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerOrderService {

    private final BrokerAdapter brokerAdapter;

    public OrderResponse placeOrder(String symbol, String strike, String direction, int quantity) {
        log.warn("💸 LIVE ORDER → {} {} {} qty={}", symbol, strike, direction, quantity);
        try {
            return brokerAdapter.placeOrder(OrderRequest.builder()
                .symbol(symbol).strike(strike).direction(direction)
                .quantity(quantity).orderType("MARKET").productType("INTRADAY").build());
        } catch (Exception e) {
            log.error("❌ Live order failed: {}", e.getMessage(), e);
            return OrderResponse.builder().status("FAILED").errorMessage(e.getMessage())
                .symbol(symbol).strike(strike).direction(direction)
                .quantity(quantity).timestamp(System.currentTimeMillis()).build();
        }
    }

    public OrderResponse squareOff(String symbol, String strike, int quantity) {
        try { return brokerAdapter.squareOff(symbol, strike, quantity); }
        catch (Exception e) {
            return OrderResponse.builder().status("FAILED").errorMessage(e.getMessage())
                .symbol(symbol).strike(strike).timestamp(System.currentTimeMillis()).build();
        }
    }
}
