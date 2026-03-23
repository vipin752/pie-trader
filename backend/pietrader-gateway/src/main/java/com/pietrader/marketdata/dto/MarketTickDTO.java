package com.pietrader.marketdata.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketTickDTO {

    private String symbol;     // NIFTY
    private String strike;     // 23200
    private String optionType; // CE / PE
    private double ltp;
    private long volume;
    private long oi;
    private double iv;
    private long timestamp;
}