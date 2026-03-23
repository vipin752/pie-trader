package com.pietrader.marketdata.dto;

import lombok.Data;

@Data
public class SymbolToken {

    private String symbol;
    private String token;
    private String exch_seg;
    private String instrumenttype;
    private String strike;
    private String expiry;
}
