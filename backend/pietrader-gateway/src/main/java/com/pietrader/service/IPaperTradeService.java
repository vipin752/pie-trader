package com.pietrader.service;

import com.pietrader.broker.model.OrderResponse;

/** Paper trade simulation — no real money. Impl: PaperTradeServiceImpl */
public interface IPaperTradeService {
    OrderResponse placeOrder(String symbol, String strike, String direction, int quantity);
    OrderResponse squareOff(String symbol, String strike, int quantity, double ltp);
}
