package com.pietrader.state;

import lombok.Data;

/**
 * PIE TRADER — TradeState
 * Redis-persisted live position state.
 * Contains all existing fields (backward compat) + contract Position fields.
 */
@Data
public class TradeState {

    // ── Existing fields (UNCHANGED — existing tests depend on these) ──────────
    private String  symbol;
    private boolean tradeActive;
    private String  direction;
    private String  strike;
    private Double  entryPrice;
    private Long    lastExecutionTime;
    private String  lastStrategy;
    private double  sl;
    private double  target;
    private int     quantity;
    private String  orderId;
    private boolean orderPending;
    private long    entryTime;
    private long    lastUpdateTime;
    private String  tradeType;

    // ── Contract §5.5 Position fields (NEW — required by PositionManager/DashboardController)
    private double  currentPrice;
    private double  trailingStop;
    private double  pnl;
    private double  rrAchieved;
    private String  positionState;   // OPEN | TRAILING | TARGET_NEAR | STOP_NEAR | CLOSED

    // ── Aliases for backward compat ───────────────────────────────────────────
    public double  getStopLoss()           { return sl; }
    public void    setStopLoss(double v)   { this.sl = v; }
    public double  getTrailingSl()         { return trailingStop; }
    public void    setTrailingSl(double v) { this.trailingStop = v; }

    // ── Computed helpers (called by DashboardController /api/positions) ───────
    public void updatePnl() {
        if (entryPrice == null || entryPrice <= 0 || quantity <= 0) return;
        double diff = "SELL".equalsIgnoreCase(direction)
            ? entryPrice - currentPrice : currentPrice - entryPrice;
        this.pnl = diff * quantity;
    }

    public void updateRR() {
        if (entryPrice == null || sl <= 0 || target <= sl || entryPrice <= 0) return;
        double risk = Math.abs(entryPrice - sl);
        if (risk > 0) this.rrAchieved = (currentPrice - entryPrice) / risk;
    }

    public void updatePositionState() {
        if (!tradeActive) { this.positionState = "CLOSED"; return; }
        if (currentPrice <= 0) { this.positionState = "OPEN"; return; }
        double distToSl  = Math.abs(currentPrice - sl);
        double distToTgt = Math.abs(target - currentPrice);
        if (trailingStop > sl)             this.positionState = "TRAILING";
        else if (distToSl < distToTgt * 0.2)  this.positionState = "STOP_NEAR";
        else if (distToTgt < distToSl * 0.2)  this.positionState = "TARGET_NEAR";
        else                               this.positionState = "OPEN";
    }
}
