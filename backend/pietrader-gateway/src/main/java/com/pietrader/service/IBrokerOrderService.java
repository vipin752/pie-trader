package com.pietrader.service;

import com.pietrader.broker.model.OrderResponse;

/** Live order placement via Angel SmartAPI. Impl: BrokerOrderServiceImpl */
public interface IBrokerOrderService {
    OrderResponse placeOrder(String symbol, String strike, String direction, int quantity);
    OrderResponse squareOff(String symbol, String strike, int quantity);
}
