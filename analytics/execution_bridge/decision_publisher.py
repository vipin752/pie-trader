"""
PIE TRADER — execution_bridge/decision_publisher.py

Publishes OptionAnalyticsDTO (as JSON) to Kafka → pie.analytics.results
Java AnalyticsConsumer reads from this topic and triggers TradingOrchestrator.

Production additions vs POC:
  - Lazy producer with auto-reconnect
  - Validates DTO has minimum required fields before publishing
  - Logs action + confidence on every publish
  - Suppresses publish if Java already has an active position for that symbol
    (position_consumer integration)
"""

from __future__ import annotations
import logging
from kafka_utils.kafka_producer import KafkaProducerService
from kafka_utils.topics import DECISION_TOPIC

logger = logging.getLogger(__name__)

# Minimum required fields — if ANY is missing, we skip the publish
_REQUIRED_PATHS = [
    ["market_context", "symbol"],
    ["confidence", "confidence_score"],
    ["auto_trade_decision", "auto_trade_decision", "action"],
]


class DecisionPublisher:
    """
    Publishes analytics results to Kafka for Java execution engine.
    Thread-safe — one instance shared across all analytics loop threads.
    """

    def __init__(self, position_consumer=None):
        """
        Args:
            position_consumer: Optional PositionEventConsumer.
                               If provided, skips publish when Java has open position.
        """
        self._producer         = None
        self._position_consumer = position_consumer

    # ── Lazy producer ─────────────────────────────────────────────────────────

    def _get_producer(self) -> KafkaProducerService:
        if self._producer is None:
            self._producer = KafkaProducerService()
        return self._producer

    # ── Publish ───────────────────────────────────────────────────────────────

    def publish(self, decision_json: dict, symbol: str = None):
        """
        Publish a validated analytics result to pie.analytics.results.

        Skipped if:
          - decision_json is missing required fields
          - Java already has an active position for this symbol
        """
        if not decision_json:
            logger.warning("⛔ DecisionPublisher: empty dict — skipping")
            return

        # Resolve symbol
        sym = (symbol or _safe_get(decision_json, ["market_context", "symbol"], "?")).upper()

        # Guard: do not re-send if Java already has an active position
        if self._position_consumer and self._position_consumer.has_active_position(sym):
            logger.debug(f"⛔ [{sym}] Java has active position — publish suppressed")
            return

        # Validate minimum required fields
        if not self._validate(decision_json, sym):
            return

        try:
            self._get_producer().send(
                DECISION_TOPIC,
                value=decision_json,
                key=sym
            )
            action     = _safe_get(decision_json, ["auto_trade_decision", "auto_trade_decision", "action"], "?")
            confidence = _safe_get(decision_json, ["confidence", "confidence_score"], "?")
            logger.info(f"📤 [{sym}] → {DECISION_TOPIC} | action={action} confidence={confidence}")

        except Exception as e:
            logger.error(f"❌ Decision publish failed for {sym}: {e}")
            self._producer = None   # Force reconnect on next call

    def close(self):
        """Flush and close Kafka producer gracefully."""
        try:
            if self._producer:
                self._producer.close()
        except Exception as e:
            logger.error(f"❌ Producer close error: {e}")

    # ── Validation ────────────────────────────────────────────────────────────

    def _validate(self, dto: dict, symbol: str) -> bool:
        for path in _REQUIRED_PATHS:
            val = _safe_get(dto, path)
            if val is None:
                logger.warning(f"⛔ [{symbol}] Missing field {'.'.join(path)} — skipping publish")
                return False

        # Skip if action is WAIT or NO_TRADE (don't spam Java)
        action = _safe_get(dto, ["auto_trade_decision", "auto_trade_decision", "action"], "")
        if action.upper() in ("WAIT", "NO_TRADE", "HOLD", ""):
            logger.debug(f"⛔ [{symbol}] action={action} — not publishing to Java")
            return False

        return True


# ── Private helpers ───────────────────────────────────────────────────────────

def _safe_get(d: dict, keys: list, default=None):
    try:
        for k in keys:
            if not isinstance(d, dict):
                return default
            d = d.get(k)
            if d is None:
                return default
        return d
    except Exception:
        return default
