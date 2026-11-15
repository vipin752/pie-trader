package com.pietrader.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

/**
 * PIE TRADER — RawOptionChainRecord
 *
 * DATA CONTRACT Section 1: NSE → Java
 *
 * Raw option chain record as received from NSE scraper / Angel API.
 * This is the unprocessed input — do not add computed fields here.
 * Derived fields belong in StrikeData.
 */
@Data
@Builder
public class RawOptionChainRecord {

    /** Index symbol e.g. NIFTY, BANKNIFTY */
    private String    symbol;

    /** Option expiry date */
    private LocalDate expiry;

    /** Strike price */
    private double    strike;

    // ── Call side ─────────────────────────────────────────────────────────────
    private long   callOI;
    private long   callOIChange;
    private long   callVolume;
    private double callIV;
    private double callLTP;

    // ── Put side ──────────────────────────────────────────────────────────────
    private long   putOI;
    private long   putOIChange;
    private long   putVolume;
    private double putIV;
    private double putLTP;

    // ── Underlying ────────────────────────────────────────────────────────────
    /** Current spot index price */
    private double spot;

    /** Futures price for current expiry */
    private double futures;

    /** Epoch milliseconds of data snapshot */
    private long   timestamp;
}
