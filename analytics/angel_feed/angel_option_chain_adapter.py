"""
PIE TRADER — angel_feed/angel_option_chain_adapter.py

Converts live tick data (from Kafka/Java) into the NSE option chain format
that OptionEngine expects.

FIX: Added spot fallback from MarketStateConsumer (Java MarketState).
  Root cause: get_spot() returns 0.0 until a SPOT tick (token 26000) arrives.
  At startup or during market-closed hours, no SPOT tick comes, so the adapter
  always returned None → "No option chain data".
  Fix: if live_chain spot is 0, try market_state_consumer.spot() as fallback.

FIX: Added minimum strikes threshold (default 3) — previously required >= 5
  which could fail early in the session when only ATM strikes are populated.
"""

import logging
from angel_feed.token_mapper import TokenMapper
from market_data.live_option_chain import LiveOptionChain
from market_data.atm_calculator import ATMCalculator

logger = logging.getLogger(__name__)

MIN_STRIKES = 3   # minimum strikes before chain is considered usable


class AngelOptionChainAdapter:

    def __init__(self, live_chain: LiveOptionChain, token_mapper: TokenMapper):
        self.live_chain           = live_chain
        self.token_mapper         = token_mapper
        self.atm_calc             = ATMCalculator()
        self.market_state_consumer = None   # injected by app.py

    def set_market_state_consumer(self, consumer):
        """Wire MarketStateConsumer for spot fallback."""
        self.market_state_consumer = consumer

    def fetch(self, symbol: str) -> dict | None:
        """
        Build option chain dict from live ticks for OptionEngine.
        Returns None only if truly no data available.

        Spot resolution priority:
          1. live_chain spot (from SPOT tick, token 26000/26009)
          2. market_state_consumer.spot() (from Java MarketStateDTO)
          3. return None — nothing to work with
        """
        symbol = symbol.upper()

        # ── SPOT (with fallback) ──────────────────────────────────────────────
        spot = self.live_chain.get_spot(symbol)

        if spot <= 0 and self.market_state_consumer:
            try:
                ms = self.market_state_consumer.get(symbol)
                if ms and ms.get("spot", 0) > 0:
                    spot = float(ms["spot"])
                    logger.info(f"📡 [{symbol}] Using Java MarketState spot={spot} as fallback")
            except Exception:
                pass

        if spot <= 0:
            logger.warning(f"⚠️ [{symbol}] No spot price — LIVE chain not ready and no MarketState")
            return None

        self.atm_calc.update_spot(symbol, spot)
        atm = self.atm_calc.get_atm(symbol)

        # ── EXPIRY ────────────────────────────────────────────────────────────
        expiry = self.token_mapper.get_nearest_expiry(symbol)
        if not expiry:
            logger.warning(f"⚠️ [{symbol}] No expiry — token_mapper not loaded yet")
            return None

        # ── BUILD STRIKES MAP ─────────────────────────────────────────────────
        all_ticks   = self.live_chain.get_full_chain()
        strikes_map: dict[int, dict] = {}

        for token, tick in all_ticks.items():
            info = self.token_mapper.get_info(token)
            if not info:
                continue
            if info["symbol"] != symbol:
                continue
            if info["expiry"] != expiry:
                continue

            strike   = info["strike"]
            opt_type = info["type"]  # CE or PE

            if strike not in strikes_map:
                strikes_map[strike] = {
                    "strike":      strike,
                    "call_ltp":    0.0, "put_ltp":    0.0,
                    "call_iv":     0.0, "put_iv":     0.0,
                    "call_oi":     0,   "put_oi":     0,
                    "call_volume": 0,   "put_volume": 0,
                    "call_bid":    0.0, "put_bid":    0.0,
                    "call_ask":    0.0, "put_ask":    0.0,
                    "call_chg_oi": 0,   "put_chg_oi": 0,
                }

            if opt_type == "CE":
                strikes_map[strike]["call_ltp"]    = tick.get("ltp", 0.0)
                strikes_map[strike]["call_oi"]     = tick.get("oi", 0)
                strikes_map[strike]["call_volume"] = tick.get("volume", 0)
                strikes_map[strike]["call_bid"]    = tick.get("bid", 0.0)
                strikes_map[strike]["call_ask"]    = tick.get("ask", 0.0)
                strikes_map[strike]["call_iv"]     = tick.get("iv", 0.0)

            elif opt_type == "PE":
                strikes_map[strike]["put_ltp"]    = tick.get("ltp", 0.0)
                strikes_map[strike]["put_oi"]     = tick.get("oi", 0)
                strikes_map[strike]["put_volume"] = tick.get("volume", 0)
                strikes_map[strike]["put_bid"]    = tick.get("bid", 0.0)
                strikes_map[strike]["put_ask"]    = tick.get("ask", 0.0)
                strikes_map[strike]["put_iv"]     = tick.get("iv", 0.0)

        if len(strikes_map) < MIN_STRIKES:
            logger.warning(
                f"⚠️ [{symbol}] Only {len(strikes_map)} strikes built "
                f"(need {MIN_STRIKES}) — ticks still flowing in"
            )
            return None

        strikes_list = sorted(strikes_map.values(), key=lambda x: x["strike"])

        logger.debug(
            f"✅ [{symbol}] Chain built: spot={spot} atm={atm} "
            f"strikes={len(strikes_list)} expiry={expiry}"
        )

        return {
            "spot":         spot,
            "atm":          atm,
            "expiry_dates": [expiry],
            "strikes":      strikes_list,
        }

    def is_ready(self, symbol: str) -> bool:
        """True if we have live data and enough strikes."""
        chain = self.fetch(symbol)
        return chain is not None and len(chain.get("strikes", [])) >= MIN_STRIKES
    