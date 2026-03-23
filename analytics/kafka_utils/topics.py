# PIE TRADER — kafka_utils/topics.py
# AUTHORITATIVE Kafka topic registry.
# All topic strings match the DATA CONTRACT exactly.
#
# ┌──────────────────────────────┬──────────────┬────────────────────────────┐
# │ Topic                        │ Direction    │ Purpose                    │
# ├──────────────────────────────┼──────────────┼────────────────────────────┤
# │ pie.market.ticks             │ Java→Python  │ Raw WS ticks               │
# │ pie.market.state             │ Java→Python  │ Derived MarketState        │
# │ pie.analytics.results        │ Python→Java  │ AnalyticsResult / decision │
# │ pie.trade.events             │ Java→*       │ Order placed/filled events │
# │ pie.position.events          │ Java→Python  │ Position open/close        │
# │ pie.squareoff.signals        │ Python→Java  │ Force-close trigger        │
# │ pie.alerts                   │ Java→*       │ System alerts              │
# └──────────────────────────────┴──────────────┴────────────────────────────┘

# ── Java → Python ─────────────────────────────────────────────────────────────

TICK_TOPIC          = "pie.market.ticks"        # Raw WebSocket ticks
MARKET_STATE_TOPIC  = "pie.market.state"        # Derived MarketState snapshot

# ── Python → Java ─────────────────────────────────────────────────────────────

DECISION_TOPIC      = "pie.analytics.results"   # Full AnalyticsResult → execution
SQUAREOFF_TOPIC     = "pie.squareoff.signals"   # Emergency close trigger

# ── Java → * ─────────────────────────────────────────────────────────────────

TRADE_EVENT_TOPIC   = "pie.trade.events"        # Order lifecycle events
POSITION_TOPIC      = "pie.position.events"     # Position open/close events
ALERTS_TOPIC        = "pie.alerts"              # System alerts

# ── Convenience sets ─────────────────────────────────────────────────────────

ALL_TOPICS = {
    TICK_TOPIC, MARKET_STATE_TOPIC, DECISION_TOPIC,
    SQUAREOFF_TOPIC, TRADE_EVENT_TOPIC, POSITION_TOPIC, ALERTS_TOPIC,
}

CONSUMER_TOPICS = {TICK_TOPIC, MARKET_STATE_TOPIC, POSITION_TOPIC}
PRODUCER_TOPICS = {DECISION_TOPIC, SQUAREOFF_TOPIC}
