package com.pietrader.execution.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Trade {
    private String    tradeId;
    private String    orderId;
    private String    symbol;
    private String    strike;
    private String    direction;
    private int       lots;
    private int       quantity;
    private double    entryPrice;
    private double    sl;
    private double    target;
    private long      entryTime;
    private TradeMode mode;
    private int       confidence;
    private String    regime;
    private String    tradeType;
    private boolean   success;
    private String    failureReason;
    private String    strategy;
}
