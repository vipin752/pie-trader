"""
option_intelligence/ingestion/nse_option_scraper.py

ROOT CAUSE FIX (Bug 4):
EXPIRY_MAP had hardcoded dates like "24-Mar-2026".
After that date, NSE API returns empty data → entire fallback breaks.

FIX: Fetch expiry list dynamically from NSE API on first call.
     Cache it per session. Still has a static fallback in case NSE is down.
"""

import requests
import urllib3
import time
import datetime
import logging

urllib3.disable_warnings()
logger = logging.getLogger(__name__)

BASE        = "https://www.nseindia.com"
OPTION_PAGE = "https://www.nseindia.com/option-chain"

# v3 with expiry param (used once we know expiry)
API_V3 = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol={symbol}&expiry={expiry}"
# v1 returns all expiries — used to discover current expiry dynamically
API_V1 = "https://www.nseindia.com/api/option-chain-indices?symbol={symbol}"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/122.0.0.0 Safari/537.36"
    ),
    "Accept":         "application/json, text/plain, */*",
    "Accept-Language":"en-US,en;q=0.9",
    "Referer":        OPTION_PAGE,
}

# Static fallback only — used when NSE API is completely unreachable
# These will be overridden by dynamic discovery in normal operation
_STATIC_FALLBACK_EXPIRY = {
    "NIFTY":      None,   # resolved dynamically
    "BANKNIFTY":  None,
    "MIDCPNIFTY": None,
    "FINNIFTY":   None,
}


def _nearest_thursday(from_date: datetime.date) -> datetime.date:
    """Returns the nearest upcoming Thursday (NSE weekly expiry day)."""
    days_ahead = (3 - from_date.weekday()) % 7   # 3 = Thursday
    if days_ahead == 0:
        days_ahead = 7
    return from_date + datetime.timedelta(days=days_ahead)


def _parse_nse_expiry_date(expiry_str: str) -> datetime.date | None:
    """Parse NSE expiry strings like '24-Mar-2026' or '24 Mar 2026'."""
    for fmt in ("%d-%b-%Y", "%d %b %Y", "%d-%B-%Y"):
        try:
            return datetime.datetime.strptime(expiry_str.strip(), fmt).date()
        except ValueError:
            continue
    return None


class NSEOptionScraper:

    def __init__(self):
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update(HEADERS)

        # Cache: symbol → (expiry_str_for_api, fetched_date)
        # Refreshed daily so it never goes stale
        self._expiry_cache: dict[str, tuple[str, datetime.date]] = {}

        self._warmup()

    def _warmup(self):
        try:
            self.session.get(BASE, timeout=10)
            time.sleep(0.5)
            self.session.get(OPTION_PAGE, timeout=10)
            time.sleep(0.5)
            logger.info("NSE session warmed up")
        except Exception as e:
            logger.warning(f"NSE warmup failed (may still work): {e}")

    # ─────────────────────────────────────────────────────────────────────────
    # DYNAMIC EXPIRY DISCOVERY
    # ─────────────────────────────────────────────────────────────────────────
    def _get_current_expiry(self, symbol: str) -> str | None:
        """
        Fetch the nearest upcoming expiry from NSE for this symbol.
        Uses v1 API which returns full expiryDates list without needing an expiry param.
        Caches per symbol, refreshed daily.
        """
        today = datetime.date.today()

        # Cache hit: same day, same symbol
        cached = self._expiry_cache.get(symbol.upper())
        if cached:
            expiry_str, cached_date = cached
            if cached_date == today:
                return expiry_str

        # Fetch fresh expiry list from NSE
        url = API_V1.format(symbol=symbol.upper())
        try:
            r = self.session.get(url, timeout=15)
            if r.status_code == 401:
                self._warmup()
                r = self.session.get(url, timeout=15)

            if r.status_code != 200:
                logger.warning(f"NSE expiry fetch HTTP {r.status_code} for {symbol}")
                return self._static_fallback_expiry(symbol)

            raw = r.json()
            expiry_dates = raw.get("records", {}).get("expiryDates", [])
            if not expiry_dates:
                logger.warning(f"No expiryDates in NSE response for {symbol}")
                return self._static_fallback_expiry(symbol)

            # Find nearest future (or today) expiry
            nearest = None
            nearest_date = None
            for e in expiry_dates:
                d = _parse_nse_expiry_date(e)
                if d is None:
                    continue
                if d >= today:
                    if nearest_date is None or d < nearest_date:
                        nearest_date = d
                        nearest = e

            if nearest is None:
                logger.warning(f"All expiries are in the past for {symbol}: {expiry_dates}")
                return self._static_fallback_expiry(symbol)

            logger.info(f"📅 Dynamic expiry for {symbol}: {nearest} (date={nearest_date})")
            self._expiry_cache[symbol.upper()] = (nearest, today)
            return nearest

        except Exception as e:
            logger.error(f"NSE expiry discovery failed for {symbol}: {e}")
            return self._static_fallback_expiry(symbol)

    def _static_fallback_expiry(self, symbol: str) -> str | None:
        """Last-resort: compute nearest Thursday as dd-Mon-YYYY."""
        today = datetime.date.today()
        thursday = _nearest_thursday(today)
        fallback = thursday.strftime("%-d-%b-%Y")   # e.g. "27-Mar-2026"
        logger.warning(f"⚠️ Using computed Thursday expiry for {symbol}: {fallback}")
        return fallback

    # ─────────────────────────────────────────────────────────────────────────
    # FETCH
    # ─────────────────────────────────────────────────────────────────────────
    def fetch(self, symbol: str) -> dict | None:
        symbol = symbol.upper()

        expiry = self._get_current_expiry(symbol)
        if not expiry:
            raise Exception(f"Cannot determine expiry for {symbol}")

        url = API_V3.format(symbol=symbol, expiry=expiry)
        logger.info(f"📡 NSE fetch: {symbol} expiry={expiry}")

        for attempt in range(1, 4):
            try:
                r = self.session.get(url, timeout=20)

                if r.status_code == 401:
                    logger.warning("NSE 401 — re-warming session")
                    self._warmup()
                    # Also invalidate expiry cache so we re-discover
                    self._expiry_cache.pop(symbol, None)
                    expiry = self._get_current_expiry(symbol)
                    url = API_V3.format(symbol=symbol, expiry=expiry)
                    continue

                if r.status_code != 200:
                    logger.warning(f"NSE HTTP {r.status_code} attempt {attempt}")
                    time.sleep(2 * attempt)
                    continue

                raw = r.json()
                records = raw.get("records", {})
                spot    = float(records.get("underlyingValue", 0))
                expiry_dates = records.get("expiryDates", [])
                strikes = []

                for row in records.get("data", []):
                    ce = row.get("CE", {})
                    pe = row.get("PE", {})
                    strikes.append({
                        "strike":      row.get("strikePrice", 0),
                        "call_oi":     ce.get("openInterest", 0),
                        "put_oi":      pe.get("openInterest", 0),
                        "call_volume": ce.get("totalTradedVolume", 0),
                        "put_volume":  pe.get("totalTradedVolume", 0),
                        "call_iv":     ce.get("impliedVolatility", 0),
                        "put_iv":      pe.get("impliedVolatility", 0),
                        "call_ltp":    ce.get("lastPrice", 0),
                        "put_ltp":     pe.get("lastPrice", 0),
                    })

                if not strikes:
                    logger.warning(f"NSE returned 0 strikes for {symbol}/{expiry} — invalidating cache")
                    self._expiry_cache.pop(symbol, None)
                    return None

                logger.info(f"✅ NSE chain: {symbol} spot={spot} strikes={len(strikes)} expiry={expiry}")
                return {
                    "symbol":       symbol,
                    "spot":         spot,
                    "expiry_dates": expiry_dates,
                    "strikes":      strikes,
                }

            except Exception as e:
                logger.warning(f"NSE fetch attempt {attempt} failed: {e}")
                time.sleep(2 * attempt)

        logger.error(f"❌ All NSE fetch attempts failed for {symbol}")
        return None
    