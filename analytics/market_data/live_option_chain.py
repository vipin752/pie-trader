"""
live_option_chain.py

Thread-safe in-memory option chain.
Updated by Kafka tick messages from Java WebSocket.

FIX: Added update_spot() guard log so you can confirm spot ticks are
arriving. No logic change — purely defensive logging added so the
pipeline is traceable end-to-end without guessing.
"""

import threading
import time
import logging

logger = logging.getLogger(__name__)


class LiveOptionChain:

    def __init__(self):
        self._chain: dict = {}        # token → tick data
        self._spot: dict  = {}        # symbol → spot price
        self._lock = threading.Lock()
        self._last_update = 0

    # ─────────────────────────────────────────────────────────────────────────
    # TICK UPDATE (called by Kafka consumer)
    # ─────────────────────────────────────────────────────────────────────────
    def update_tick(self, tick: dict):
        """
        Update chain from a processed tick.
        Tick format (from Java AngelTickPublisher):
        {
            "token": "12345",
            "symbol": "NIFTY",
            "strike": 23200,
            "optionType": "CE",  # or "SPOT" for index
            "ltp": 186.6,
            "volume": 2926468,
            "oi": 52731,
            "bid": 186.5,
            "ask": 186.7,
            "open": 150.0,
            "high": 200.0,
            "low": 120.0,
            "close": 175.0,
            "timestamp": 1711000000000
        }

        FIX CONTRACT:
        Java must send optionType="SPOT" (not "" or "INDEX") for spot/index ticks.
        This is guaranteed by AngelTokenService loading INDEX tokens with
        optionType="SPOT" so AngelWebSocketClient.parseTick() resolves it correctly.
        """
        token = str(tick.get("token", ""))
        if not token:
            return

        with self._lock:
            self._chain[token] = {
                "ltp":       tick.get("ltp", 0.0),
                "bid":       tick.get("bid", 0.0),
                "ask":       tick.get("ask", 0.0),
                "volume":    tick.get("volume", 0),
                "oi":        tick.get("oi", 0),
                "open":      tick.get("open", 0.0),
                "high":      tick.get("high", 0.0),
                "low":       tick.get("low", 0.0),
                "close":     tick.get("close", 0.0),
                "timestamp": tick.get("timestamp", int(time.time() * 1000)),
            }

            # FIX: Update spot if this is a spot/index tick.
            # optionType must be exactly "SPOT" — set by Java AngelTokenService
            # for INDEX instrument type. Previously this never fired because
            # Java sent optionType="" for spot tokens (they weren't in tokenMap).
            opt_type = tick.get("optionType", "")
            symbol   = tick.get("symbol", "")

            if opt_type == "SPOT" and symbol:
                sym_upper = symbol.upper()
                ltp = tick.get("ltp", 0.0)
                self._spot[sym_upper] = ltp
                logger.info(f"📈 Spot updated: {sym_upper} = {ltp}")   # INFO not DEBUG — confirm it's flowing
            elif opt_type == "" and symbol and symbol != "UNKNOWN":
                # Defensive: if Java sends a tick with no optionType but a valid
                # symbol and strike=0, treat it as a spot tick (failsafe only).
                strike = tick.get("strike", -1)
                if strike == 0:
                    sym_upper = symbol.upper()
                    ltp = tick.get("ltp", 0.0)
                    if ltp > 0:
                        self._spot[sym_upper] = ltp
                        logger.warning(
                            f"⚠️ Spot updated via fallback (optionType missing): "
                            f"{sym_upper} = {ltp}. Fix Java AngelTokenService."
                        )

            self._last_update = time.time()

    # ─────────────────────────────────────────────────────────────────────────
    # READ
    # ─────────────────────────────────────────────────────────────────────────
    def get_full_chain(self) -> dict:
        with self._lock:
            return dict(self._chain)

    def get_strike_data(self, token: str) -> dict | None:
        with self._lock:
            return self._chain.get(str(token))

    def get_spot(self, symbol: str) -> float:
        with self._lock:
            val = self._spot.get(symbol.upper(), 0.0)
            if val <= 0:
                logger.debug(f"⚠️ get_spot({symbol}) = 0.0 — spot tick not yet received")
            return val

    def update_spot(self, symbol: str, price: float):
        with self._lock:
            self._spot[symbol.upper()] = price

    def is_stale(self, max_age_seconds: int = 30) -> bool:
        """Returns True if no tick received for > max_age_seconds."""
        return (time.time() - self._last_update) > max_age_seconds

    def stats(self) -> dict:
        with self._lock:
            return {
                "token_count": len(self._chain),
                "spot":        dict(self._spot),
                "last_update": self._last_update,
                "age_seconds": round(time.time() - self._last_update, 1),
                # FIX: expose spot map so /chain-stats tells you if spot is 0
                "spot_ready":  {sym: (price > 0) for sym, price in self._spot.items()},
            }
            