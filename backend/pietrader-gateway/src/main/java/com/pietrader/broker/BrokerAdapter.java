package com.pietrader.broker;

import com.pietrader.broker.model.ActivePosition;
import com.pietrader.broker.model.OrderRequest;
import com.pietrader.broker.model.OrderResponse;

import java.util.List;

/**
 * PHASE 4 — Broker Abstraction Layer
 *
 * Today: Angel One
 * Tomorrow: Zerodha / Fyers / Dhan — zero code change in core system
 */
public interface BrokerAdapter {

    /** Place a new order */
    OrderResponse placeOrder(OrderRequest request);

    /** Cancel an existing order */
    boolean cancelOrder(String orderId);

    /** Get active position for a symbol */
    ActivePosition getPosition(String symbol);

    /** Get all open positions */
    List<ActivePosition> getAllPositions();

    /** Square off (exit) a position */
    OrderResponse squareOff(String symbol, String strike, int quantity);
}
