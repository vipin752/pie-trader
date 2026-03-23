package com.pietrader.model;

import lombok.Builder;
import lombok.Data;

/**
 * PIE TRADER — StrikeData
 *
 * DATA CONTRACT Section 2: Java Internal Derived
 *
 * Computed from RawOptionChainRecord. Included inside MarketState.strikes list.
 * This is sent Java → Python via Kafka (pie.market.state).
 */
@Data
@Builder
public class StrikeData {

    private double strike;

    // ── Raw chain ─────────────────────────────────────────────────────────────
    private long   callOI;
    private long   putOI;
    private long   callVolume;
    private long   putVolume;
    private double callIV;
    private double putIV;
    private double callLTP;
    private double putLTP;

    // ── Derived ───────────────────────────────────────────────────────────────
    /** (callOI - putOI) / (callOI + putOI) — positive = call heavy */
    private double oiImbalance;

    /** (callVolume - putVolume) / (callVolume + putVolume) */
    private double volumeImbalance;

    /** callIV - putIV — positive = call premium (fear) */
    private double ivSkew;

    /** Net gamma at this strike (dealer hedge pressure) */
    private double gamma;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Build a StrikeData from a raw record, computing all derived fields.
     */
    public static StrikeData from(RawOptionChainRecord raw) {
        long   totalOI  = raw.getCallOI() + raw.getPutOI();
        long   totalVol = raw.getCallVolume() + raw.getPutVolume();

        double oiImbal  = totalOI  > 0
            ? (double)(raw.getCallOI()     - raw.getPutOI())     / totalOI  : 0.0;
        double volImbal = totalVol > 0
            ? (double)(raw.getCallVolume() - raw.getPutVolume()) / totalVol : 0.0;
        double ivSkew   = raw.getCallIV() - raw.getPutIV();

        return StrikeData.builder()
            .strike(raw.getStrike())
            .callOI(raw.getCallOI())
            .putOI(raw.getPutOI())
            .callVolume(raw.getCallVolume())
            .putVolume(raw.getPutVolume())
            .callIV(raw.getCallIV())
            .putIV(raw.getPutIV())
            .callLTP(raw.getCallLTP())
            .putLTP(raw.getPutLTP())
            .oiImbalance(oiImbal)
            .volumeImbalance(volImbal)
            .ivSkew(ivSkew)
            .gamma(0.0)   // populated by GammaEngine after chain is built
            .build();
    }
}
