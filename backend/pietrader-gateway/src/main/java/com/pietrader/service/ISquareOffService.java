package com.pietrader.service;

import com.pietrader.broker.model.OrderResponse;

import java.util.List;

/** Handles all position exit scenarios. Impl: SquareOffServiceImpl */
public interface ISquareOffService {
    OrderResponse       squareOff(String symbol, String reason);
    List<OrderResponse> squareOffAll(String reason);
}
