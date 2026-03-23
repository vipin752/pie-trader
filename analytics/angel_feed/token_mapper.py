"""
token_mapper.py

Loads Angel One OpenAPIScripMaster.json contract file.
Priority:
  1. Local file (config/CONTRACT_FILE_PATH) — fast, no network
  2. URL download — fallback if local missing

ROOT CAUSE FIX:
Old code checked instrumenttype == "INDEX" for spot tokens.
Real spot tokens in the contract have instrumenttype = "" (empty string):
  token=26000  name=NIFTY      instrumenttype=""      exch_seg=NSE
  token=26009  name=BANKNIFTY  instrumenttype=""      exch_seg=NSE
  token=26037  name=FINNIFTY   instrumenttype=""      exch_seg=NSE
  token=26074  name=MIDCPNIFTY instrumenttype=""      exch_seg=NSE
  (AMXIDX variants 99926000-series also exist as alternates)

Old code → spot_tokens dict was always empty → get_spot_token("NIFTY") = None
→ Java never subscribed spot token → no spot ticks → LiveOptionChain.get_spot() = 0.0
→ AngelOptionChainAdapter.fetch() returns None → "No option chain data"

FIX: Detect spot tokens by exch_seg=NSE + name in SUPPORTED_SYMBOLS
     + instrumenttype in ("", "AMXIDX"). Prefer shorter token (26000 > 99926000).
"""

import os
import json
import datetime
import requests
import logging
from config.settings import Settings
from config.constants import STRIKE_GAPS, ATM_STRIKE_RANGE, SUPPORTED_SYMBOLS

logger = logging.getLogger(__name__)

# instrumenttype values that identify spot/index tokens (not options, not futures)
_SPOT_INSTRUMENT_TYPES = {"", "AMXIDX"}


class TokenMapper:

    def __init__(self):
        self.token_map: dict = {}        # token → {symbol, strike, type, expiry, name}
        self.spot_tokens: dict = {}      # symbol → spot token  e.g. "NIFTY" → "26000"
        self.expiry_map: dict = {}       # symbol → sorted list of expiries
        self.load_master()

    # ─────────────────────────────────────────────────────────────────────────
    # LOAD MASTER CONTRACT (LOCAL FIRST, URL FALLBACK)
    # ─────────────────────────────────────────────────────────────────────────
    def load_master(self):
        data = self._load_local() or self._load_from_url()
        if not data:
            logger.error("❌ Failed to load master contract from any source")
            return

        self._parse(data)
        logger.info(
            f"✅ Loaded {len(self.token_map)} option tokens, "
            f"{len(self.spot_tokens)} spot tokens | spot map: {self.spot_tokens}"
        )

    def _load_local(self):
        path = Settings.CONTRACT_FILE_PATH
        if os.path.exists(path):
            logger.info(f"📥 Loading contract from local file: {path}")
            try:
                with open(path, "r") as f:
                    return json.load(f)
            except Exception as e:
                logger.error(f"❌ Local file read error: {e}")
        return None

    def _load_from_url(self):
        url = Settings.CONTRACT_URL
        logger.info(f"📥 Downloading contract from URL: {url}")
        try:
            resp = requests.get(url, timeout=30)
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            logger.error(f"❌ URL download failed: {e}")
            return None

    # ─────────────────────────────────────────────────────────────────────────
    # PARSE CONTRACT
    # ─────────────────────────────────────────────────────────────────────────
    def _parse(self, data: list):
        for item in data:
            token        = item.get("token", "")
            symbol       = item.get("symbol", "")
            name         = item.get("name", "")
            expiry       = item.get("expiry", "")
            strike_raw   = item.get("strike", "0")
            instrument   = item.get("instrumenttype", "")   # may be "" for spot tokens
            exch         = item.get("exch_seg", "")

            # ── OPTIONS (OPTIDX on NFO) — unchanged ─────────────────────────
            if instrument == "OPTIDX" and exch == "NFO":
                try:
                    strike = int(float(strike_raw) / 100)
                except ValueError:
                    continue

                opt_type = "CE" if symbol.endswith("CE") else "PE"
                base = name.upper()

                self.token_map[token] = {
                    "symbol":  base,
                    "strike":  strike,
                    "type":    opt_type,
                    "expiry":  expiry,
                    "name":    symbol,   # full trading symbol e.g. NIFTY24MAR23200CE
                    "exch":    exch,
                }

                if base not in self.expiry_map:
                    self.expiry_map[base] = set()
                self.expiry_map[base].add(expiry)
                continue

            # ── FIX: SPOT TOKENS ─────────────────────────────────────────────
            # Spot tokens for Angel SmartStream have:
            #   exch_seg = "NSE"
            #   instrumenttype = ""  (empty!) or "AMXIDX"
            #   name = "NIFTY" / "BANKNIFTY" / etc.
            #
            # Old code: if instrument == "INDEX" → never matched → spot_tokens always empty
            # Fix: match on "" or "AMXIDX" on NSE exchange with a supported name
            if exch == "NSE" and instrument in _SPOT_INSTRUMENT_TYPES:
                name_upper = name.upper()
                if name_upper not in [s.upper() for s in SUPPORTED_SYMBOLS]:
                    continue
                if not token:
                    continue

                # Prefer shorter token (26000) over AMXIDX (99926000)
                existing = self.spot_tokens.get(name_upper)
                if existing is None or len(token) <= len(existing):
                    self.spot_tokens[name_upper] = token
                    logger.debug(f"📍 Spot token: {name_upper} → {token} (type={instrument!r})")

        # Sort expiry maps
        for sym in self.expiry_map:
            try:
                self.expiry_map[sym] = sorted(
                    self.expiry_map[sym],
                    key=lambda x: datetime.datetime.strptime(x, "%d%b%Y")
                )
            except Exception:
                self.expiry_map[sym] = sorted(self.expiry_map[sym])

    # ─────────────────────────────────────────────────────────────────────────
    # QUERIES — all unchanged
    # ─────────────────────────────────────────────────────────────────────────
    def get_info(self, token: str) -> dict | None:
        return self.token_map.get(str(token))

    def get_nearest_expiry(self, symbol: str) -> str | None:
        expiries = self.expiry_map.get(symbol.upper(), [])
        return expiries[0] if expiries else None

    def get_spot_token(self, symbol: str) -> str | None:
        return self.spot_tokens.get(symbol.upper())

    def get_atm_tokens(self, symbol: str, atm: int) -> list[str]:
        """Get all option tokens around ATM for nearest expiry."""
        expiry = self.get_nearest_expiry(symbol)
        if not expiry:
            return []

        gap    = STRIKE_GAPS.get(symbol.upper(), 50)
        tokens = []

        for token, info in self.token_map.items():
            if info["symbol"] != symbol.upper():
                continue
            if info["expiry"] != expiry:
                continue
            if abs(info["strike"] - atm) <= ATM_STRIKE_RANGE * gap:
                tokens.append(token)

        return tokens

    def get_all_tokens_for_symbol(self, symbol: str) -> list[str]:
        """All tokens for nearest expiry of symbol."""
        expiry = self.get_nearest_expiry(symbol)
        return [
            t for t, info in self.token_map.items()
            if info["symbol"] == symbol.upper() and info["expiry"] == expiry
        ]

    def find_token_for_order(self, symbol: str, strike: int, opt_type: str) -> str | None:
        """Find token for order placement. Matches nearest expiry + exact strike."""
        expiry = self.get_nearest_expiry(symbol)
        for token, info in self.token_map.items():
            if (info["symbol"] == symbol.upper() and
                    info["expiry"] == expiry and
                    info["strike"] == strike and
                    info["type"].upper() == opt_type.upper()):
                return token
        return None

    def get_subscriptions(self, symbols: list[str]) -> dict[str, list[str]]:
        """
        Returns {symbol: [tokens]} for all supported symbols.
        Java WebSocket uses these tokens for subscription.
        Now includes the spot token for each symbol.
        """
        result = {}
        for sym in symbols:
            result[sym] = self.get_all_tokens_for_symbol(sym)
            spot = self.get_spot_token(sym)
            if spot:
                result[sym].append(spot)
        return result
    