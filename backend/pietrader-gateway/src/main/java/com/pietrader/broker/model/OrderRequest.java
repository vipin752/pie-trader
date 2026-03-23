package com.pietrader.broker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    private String symbol;        // NIFTY / BANKNIFTY
    private String strike;        // 23000 PE
    private String direction;     // UP / DOWN
    private int    quantity;      // lot size based
    private String orderType;     // MARKET / LIMIT
    private String productType;   // INTRADAY / DELIVERY
    private Double limitPrice;    // only for LIMIT orders
}
