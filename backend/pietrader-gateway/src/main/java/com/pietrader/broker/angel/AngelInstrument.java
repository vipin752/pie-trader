package com.pietrader.broker.angel;

import lombok.Data;

@Data
public class AngelInstrument {

    private String token;
    private String symbol;
    private String name;
    private String expiry;
    private String strike;
    private String instrumentType;
    private String exchSeg;
    private String optionType;

    private int strikePrice;
}
