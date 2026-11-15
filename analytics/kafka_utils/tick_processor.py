"""
tick_processor.py

Processes raw tick messages published by Java AngelTickPublisher.
Java publishes MarketTickDTO as JSON to pie.market.ticks.
"""

import time
import logging

logger = logging.getLogger(__name__)


class TickProcessor:

    def process(self, raw: dict) -> dict | None:
        """
        Normalise a tick from Java's MarketTickDTO JSON format.
        Java sends:
        {
            "token": "12345",
            "symbol": "NIFTY",
            "strike": "23200",      (string from Java)
            "optionType": "CE",
            "ltp": 186.6,
            "volume": 2926468,
            "oi": 52731,
            "timestamp": 1711000000000
        }
        """
        if not raw:
            return None

        try:
            strike_raw = raw.get("strike", 0)
            strike = int(strike_raw) if strike_raw else 0
        except (ValueError, TypeError):
            strike = 0

        return {
            "token":      str(raw.get("token", "")),
            "symbol":     raw.get("symbol", ""),
            "strike":     strike,
            "optionType": raw.get("optionType", ""),
            "ltp":        float(raw.get("ltp", 0.0)),
            "bid":        float(raw.get("bid", 0.0)),
            "ask":        float(raw.get("ask", 0.0)),
            "volume":     int(raw.get("volume", 0)),
            "oi":         int(raw.get("oi", 0)),
            "iv":         float(raw.get("iv", 0.0)),
            "open":       float(raw.get("open", 0.0)),
            "high":       float(raw.get("high", 0.0)),
            "low":        float(raw.get("low", 0.0)),
            "close":      float(raw.get("close", 0.0)),
            "timestamp":  raw.get("timestamp", int(time.time() * 1000)),
        }
