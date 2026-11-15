package com.pietrader.journal.model;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class JournalEntry {
    private String  tradeId;
    private String  symbol;
    private String  strike;
    private String  direction;
    private int     lots;
    private double  entryPrice;
    private double  sl;
    private double  target;
    private int     confidence;
    private String  regime;
    private String  strategy;
    private String  tradeType;
    private String  mode;
    private Double  gammaExposure;
    private String  ivContext;
    private Double  pcrValue;
    private Double  fearIndex;
    private String  sessionPhase;
    private String  decisionReason;
    private String  rawDecisionJson;
    private long    recordedAt;
}
