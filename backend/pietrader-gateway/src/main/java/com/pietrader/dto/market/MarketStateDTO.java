package com.pietrader.dto.market;

import com.pietrader.model.StrikeData;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * PIE TRADER — MarketStateDTO
 *
 * DATA CONTRACT Section 2: Java → Python via Kafka (pie.market.state)
 *
 * This is the COMPLETE market snapshot that Python intelligence engines consume.
 * All fields are exactly as specified in the contract — do not rename.
 *
 * Published by: MarketStateKafkaProducer (Java)
 * Consumed by:  market_state_consumer.py (Python)
 */
@Data
@Builder
public class MarketStateDTO {

    // ── Identity ──────────────────────────────────────────────────────────────
    private String symbol;           // NIFTY / BANKNIFTY / FINNIFTY / MIDCPNIFTY

    // ── Price ─────────────────────────────────────────────────────────────────
    private double spot;
    private double futures;
    private double atm;              // ATM strike (rounded to nearest gap)

    // ── Options analytics ─────────────────────────────────────────────────────
    private double pcr;              // Put-Call Ratio (OI based)
    private double maxPain;          // Max pain strike
    private double gammaExposure;    // Net GEX
    private double gammaFlip;        // Gamma flip level
    private double callWall;         // Highest call OI strike
    private double putWall;          // Highest put OI strike

    // ── Market microstructure ─────────────────────────────────────────────────
    private double vwap;
    private double iv;               // ATM IV
    private double ivRank;           // IV rank 0–100
    private double oiChange;         // Net OI change this session
    private double volumeDelta;      // Call volume - put volume

    // ── Regime / context ─────────────────────────────────────────────────────
    private String marketStructure;  // BULLISH / BEARISH / SIDEWAYS / BREAKOUT
    private String liquidity;        // HIGH / MEDIUM / LOW
    private String regime;           // TRENDING_UP / TRENDING_DOWN / RANGE / VOLATILE
    private String volatilityRegime; // HIGH_IV / LOW_IV / EXPANDING / CONTRACTING
    private String session;          // PRE_OPEN / OPENING / MIDDAY / CLOSING
    private String newsImpact;       // NONE / LOW / HIGH

    // ── Key levels ────────────────────────────────────────────────────────────
    private double support;
    private double resistance;

    // ── Full strike ladder ────────────────────────────────────────────────────
    /** All strikes within ATM range — each has callOI, putOI, IV, LTP, gamma etc. */
    private List<StrikeData> strikes;

    /** Epoch milliseconds */
    private long timestamp;
}
