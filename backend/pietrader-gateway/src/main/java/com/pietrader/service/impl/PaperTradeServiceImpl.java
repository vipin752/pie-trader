package com.pietrader.service.impl;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.service.IPaperTradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaperTradeServiceImpl implements IPaperTradeService {

    @Override
    public OrderResponse placeOrder(String symbol, String strike, String direction, int quantity) {
        String orderId = "PAPER_" + System.currentTimeMillis();
        log.info("📄 PAPER TRADE → {} {} {} qty={} orderId={}", symbol, strike, direction, quantity, orderId);
        return OrderResponse.builder()
            .status("PAPER").orderId(orderId)
            .symbol(symbol).strike(strike).direction(direction)
            .quantity(quantity).price(0.0)
            .timestamp(System.currentTimeMillis()).build();
    }

    @Override
    public OrderResponse squareOff(String symbol, String strike, int quantity, double ltp) {
        String orderId = "PAPER_EXIT_" + System.currentTimeMillis();
        log.info("📄 PAPER EXIT → {} {} qty={} ltp={}", symbol, strike, quantity, ltp);
        return OrderResponse.builder()
            .status("PAPER").orderId(orderId)
            .symbol(symbol).strike(strike)
            .quantity(quantity).price(ltp)
            .timestamp(System.currentTimeMillis()).build();
    }
}
