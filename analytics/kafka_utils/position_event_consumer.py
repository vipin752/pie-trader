"""
position_event_consumer.py

Consumes pie.position.events from Java.
Python uses this to know which symbols currently have an active trade,
so it suppresses EXECUTE signals for those symbols.

This is the feedback loop: Java → Python.
"""

import logging
import threading
from kafka_utils.kafka_consumer import KafkaConsumerService
from kafka_utils.topics import POSITION_TOPIC

logger = logging.getLogger(__name__)


class PositionEventConsumer:
    """
    Tracks active positions reported by Java execution engine.

    Java publishes to pie.position.events when:
      - A trade is OPENED  → Python should NOT send EXECUTE for that symbol
      - A trade is CLOSED  → Python can resume EXECUTE signals
    """

    def __init__(self):
        # symbol → position status ("OPEN" | "CLOSED" | "PENDING")
        self._active_positions: dict[str, str] = {}
        self._lock = threading.Lock()

    def start(self):
        """Start background consumer thread."""
        thread = threading.Thread(
            target=self._run,
            name="PositionEventConsumer",
            daemon=True
        )
        thread.start()
        logger.info("✅ PositionEventConsumer started")

    def _run(self):
        consumer = KafkaConsumerService(
            POSITION_TOPIC,
            group_id="pie-position-tracker"
        )
        consumer.listen(self._handle_event)

    def _handle_event(self, event: dict):
        try:
            symbol = event.get("symbol", "").upper()
            status = event.get("status", "").upper()

            if not symbol:
                return

            with self._lock:
                if status in ("OPEN", "PENDING"):
                    self._active_positions[symbol] = status
                    logger.info(f"🔒 Position OPEN for {symbol} — EXECUTE suppressed")
                elif status in ("CLOSED", "REJECTED", "NONE"):
                    self._active_positions.pop(symbol, None)
                    logger.info(f"🔓 Position CLOSED for {symbol} — EXECUTE resumed")

        except Exception as e:
            logger.error(f"❌ PositionEvent handle error: {e}")

    def has_active_position(self, symbol: str) -> bool:
        """Returns True if Java has an open/pending trade for this symbol."""
        with self._lock:
            status = self._active_positions.get(symbol.upper())
            return status in ("OPEN", "PENDING")

    def get_all(self) -> dict:
        with self._lock:
            return dict(self._active_positions)
