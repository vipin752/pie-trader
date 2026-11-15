"""
PIE TRADER — market_data/market_state_consumer.py

Consumes MarketState messages from Kafka topic: pie.market.state
Published by: Java MarketStateKafkaProducer

MarketState gives Python intelligence engines:
  - spot, atm, pcr, gammaExposure, gammaFlip, callWall, putWall
  - iv, ivRank, oiChange, volumeDelta
  - regime, volatilityRegime, session, newsImpact
  - support, resistance
  - full strikes list with OI, IV, LTP per strike

This consumer feeds LiveOptionChain so it can also populate from
Java-derived state (not just raw ticks).
"""

from __future__ import annotations
import logging
import threading
from typing import Dict, Optional

from kafka_utils.kafka_consumer import KafkaConsumerService
from kafka_utils.topics import MARKET_STATE_TOPIC

logger = logging.getLogger(__name__)


class MarketStateConsumer:
    """
    Consumes pie.market.state and caches the latest state per symbol.
    Thread-safe — read by analytics engines on every cycle.
    """

    def __init__(self):
        self._states: Dict[str, dict] = {}
        self._lock = threading.Lock()

    def start(self):
        """Start background consumer thread."""
        thread = threading.Thread(
            target=self._run,
            name="MarketStateConsumer",
            daemon=True
        )
        thread.start()
        logger.info("▶ MarketStateConsumer started ← pie.market.state")

    def _run(self):
        consumer = KafkaConsumerService(MARKET_STATE_TOPIC, group_id="pie-market-state-consumer")
        consumer.listen(self._handle)

    def _handle(self, state: dict):
        try:
            symbol = state.get("symbol", "").upper()
            if not symbol:
                return
            with self._lock:
                self._states[symbol] = state
            logger.debug(f"📡 MarketState received → {symbol} "
                         f"spot={state.get('spot')} regime={state.get('regime')}")
        except Exception as e:
            logger.error(f"❌ MarketState handle error: {e}")

    def get(self, symbol: str) -> Optional[dict]:
        """Return latest MarketState for symbol, or None."""
        with self._lock:
            return self._states.get(symbol.upper())

    def get_all(self) -> Dict[str, dict]:
        with self._lock:
            return dict(self._states)

    def is_ready(self, symbol: str) -> bool:
        """Returns True if at least one MarketState has been received."""
        return self.get(symbol) is not None

    # ── Field accessors (safe getters) ────────────────────────────────────────

    def spot(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("spot", 0.0) if s else 0.0

    def atm(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("atm", 0.0) if s else 0.0

    def pcr(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("pcr", 0.0) if s else 0.0

    def regime(self, symbol: str) -> str:
        s = self.get(symbol)
        return s.get("regime", "UNKNOWN") if s else "UNKNOWN"

    def iv(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("iv", 0.0) if s else 0.0

    def gamma_exposure(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("gammaExposure", 0.0) if s else 0.0

    def gamma_flip(self, symbol: str) -> float:
        s = self.get(symbol)
        return s.get("gammaFlip", 0.0) if s else 0.0
