"""
PIE TRADER — execution_bridge/squareoff_publisher.py

Publishes squareoff signals to Kafka topic: pie.squareoff.signals
Consumed by: Java SquareoffConsumer

Contract §9 message format:
{
  "symbol":    str   — index symbol OR "ALL" for emergency close
  "action":    str   — always "SQUAREOFF"
  "reason":    str   — RISK_BREACH | MAX_LOSS | GAMMA_FLIP | MANUAL | EOD
  "timestamp": long  — epoch millis
}

Use cases:
  - Python RiskEngine detects max loss exceeded
  - Regime detects extreme danger zone
  - Manual emergency from monitoring script
"""

from __future__ import annotations
import logging
import time

from kafka_utils.kafka_producer import KafkaProducerService
from kafka_utils.topics import SQUAREOFF_TOPIC

logger = logging.getLogger(__name__)


class SquareoffPublisher:
    """
    Publishes emergency squareoff signals to Java execution engine.
    Use only for emergency exits — ExitEngine handles normal SL/target.
    """

    def __init__(self):
        self._producer = None

    def _get_producer(self) -> KafkaProducerService:
        if self._producer is None:
            self._producer = KafkaProducerService()
        return self._producer

    def squareoff(self, symbol: str, reason: str):
        """
        Trigger squareoff for a specific symbol.

        Args:
            symbol: Index symbol e.g. NIFTY, or "ALL" for all positions.
            reason: Human-readable reason code e.g. RISK_BREACH, MAX_LOSS.
        """
        payload = self._build(symbol.upper(), reason)
        self._send(payload, key=symbol.upper())
        logger.warning(f"🚨 SQUAREOFF SIGNAL sent → symbol={symbol} reason={reason}")

    def squareoff_all(self, reason: str = "EMERGENCY"):
        """Trigger immediate close of ALL open positions."""
        payload = self._build("ALL", reason)
        self._send(payload, key="ALL")
        logger.warning(f"🚨 SQUAREOFF ALL sent → reason={reason}")

    def _build(self, symbol: str, reason: str) -> dict:
        """Build contract-compliant squareoff message."""
        return {
            "symbol":    symbol,
            "action":    "SQUAREOFF",
            "reason":    reason,
            "timestamp": int(time.time() * 1000),
        }

    def _send(self, payload: dict, key: str):
        try:
            self._get_producer().send(SQUAREOFF_TOPIC, value=payload, key=key)
        except Exception as e:
            logger.error(f"❌ Squareoff publish failed: {e}")
            self._producer = None

    def close(self):
        try:
            if self._producer:
                self._producer.close()
        except Exception:
            pass
