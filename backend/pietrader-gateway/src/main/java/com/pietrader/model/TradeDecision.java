package com.pietrader.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

/**
 * PIE TRADER — TradeDecision
 *
 * DATA CONTRACT Section 4: Java Execution Object
 *
 * Intermediate object created by TradingOrchestrator from AnalyticsResult.
 * Passed to OrderManager for execution.
 *
 * Fields exactly match contract — do not rename.
 */
@Data
@Builder
public class TradeDecision {

    private String    symbol;           // NIFTY / BANKNIFTY etc.
    private String    strategy;         // MOMENTUM / REVERSAL / BREAKOUT / BTST
    private String    strike;           // Full option symbol e.g. NIFTY24APR23200CE
    private LocalDate expiry;           // Option expiry date
    private String    entryType;        // MARKET / LIMIT
    private String    executionTiming;  // IMMEDIATE / ON_OPEN / ON_BREAKOUT
    private double    stopLoss;
    private double    target;
    private int       confidence;       // 0–100
    private int       lotSize;
    private String    mode;             // PAPER / AUTO / MANUAL
}
