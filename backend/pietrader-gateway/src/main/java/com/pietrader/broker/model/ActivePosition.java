package com.pietrader.broker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivePosition {

    private String  symbol;
    private String  strike;
    private String  direction;
    private Integer quantity;
    private Double  entryPrice;
    private Double  currentPrice;
    private Double  unrealisedPnl;
    private String  productType;
    private String  orderId;
}
