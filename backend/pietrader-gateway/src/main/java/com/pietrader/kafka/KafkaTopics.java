package com.pietrader.kafka;

/**
 * PIE TRADER — KafkaTopics
 *
 * AUTHORITATIVE Kafka topic registry.
 * All topics defined here exactly as specified in the DATA CONTRACT.
 *
 * ┌─────────────────────────────┬──────────────┬────────────────────────────┐
 * │ Topic                       │ Direction    │ Purpose                    │
 * ├─────────────────────────────┼──────────────┼────────────────────────────┤
 * │ pie.market.ticks            │ Java→Python  │ Raw WS ticks               │
 * │ pie.market.state            │ Java→Python  │ Derived MarketState        │
 * │ pie.analytics.results       │ Python→Java  │ AnalyticsResult / decision │
 * │ pie.trade.events            │ Java→*       │ Order placed/filled events │
 * │ pie.position.events         │ Java→Python  │ Position open/close        │
 * │ pie.squareoff.signals       │ Python→Java  │ Force-close trigger        │
 * │ pie.alerts                  │ Java→*       │ System alerts              │
 * └─────────────────────────────┴──────────────┴────────────────────────────┘
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // ── Java → Python ──────────────────────────────────────────────────────────

    /** Raw WebSocket ticks from Angel broker */
    public static final String MARKET_TICKS   = "pie.market.ticks";

    /** Derived MarketState snapshot (OI, IV, regime, gamma, PCR etc.) */
    public static final String MARKET_STATE   = "pie.market.state";

    // ── Python → Java ──────────────────────────────────────────────────────────

    /** Full AnalyticsResult — intelligence + decision from Python */
    public static final String ANALYTICS_RESULTS = "pie.analytics.results";

    /** Squareoff signal — Python or Risk engine triggers emergency close */
    public static final String SQUAREOFF_SIGNALS = "pie.squareoff.signals";

    // ── Java → * ──────────────────────────────────────────────────────────────

    /** Trade lifecycle events (PLACED / FILLED / REJECTED / CLOSED) */
    public static final String TRADE_EVENTS   = "pie.trade.events";

    /** Position lifecycle events (OPEN / CLOSED / UPDATED) */
    public static final String POSITION_EVENTS = "pie.position.events";

    /** System alerts (RISK_BREACH / MAX_LOSS / CONNECTIVITY etc.) */
    public static final String ALERTS         = "pie.alerts";
}
