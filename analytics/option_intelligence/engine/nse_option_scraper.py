from __future__ import annotations
import time, logging, requests

logger = logging.getLogger(__name__)

WARMUP_PAGES = [
    "https://www.nseindia.com",
    "https://www.nseindia.com/option-chain",
    "https://www.nseindia.com/market-data/equity-derivatives-watch",
]

API_ENDPOINTS = [
    "https://www.nseindia.com/api/option-chain-indices?symbol={symbol}",
    "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol={symbol}",
]

HEADERS = {
    "User-Agent":       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept":           "application/json, text/plain, */*",
    "Accept-Language":  "en-US,en;q=0.9",
    "Accept-Encoding":  "gzip, deflate, br",
    "Connection":       "keep-alive",
    "DNT":              "1",
    "Cache-Control":    "no-cache",
    "Pragma":           "no-cache",
    "Sec-Fetch-Dest":   "empty",
    "Sec-Fetch-Mode":   "cors",
    "Sec-Fetch-Site":   "same-origin",
    "X-Requested-With": "XMLHttpRequest",
}

TIMEOUT   = 20
MAX_RETRY = 3


class NSEOptionScraper:

    def __init__(self):
        self._session        = None
        self._cookies_loaded = False

    def _new_session(self):
        s = requests.Session()
        s.headers.update(HEADERS)
        return s

    def fetch(self, symbol="NIFTY"):
        symbol = symbol.upper()
        for attempt in range(1, MAX_RETRY + 1):
            try:
                logger.info("Attempt %d/%d for %s", attempt, MAX_RETRY, symbol)
                if not self._cookies_loaded or self._session is None:
                    self._session = self._new_session()
                    self._warm_up()
                data = self._fetch_chain(symbol)
                if data:
                    return data
                logger.warning("Empty response — forcing session refresh")
                self._cookies_loaded = False
            except Exception as exc:
                logger.warning("Attempt %d failed: %s", attempt, exc)
                self._cookies_loaded = False
                time.sleep(3 * attempt)
        logger.error("All %d attempts failed for %s", MAX_RETRY, symbol)
        return None

    def _warm_up(self):
        for url in WARMUP_PAGES:
            try:
                r = self._session.get(url, timeout=TIMEOUT,
                    headers={**HEADERS, "Referer": "https://www.nseindia.com"})
                logger.info("Warmup %s -> %d  cookies=%d", url, r.status_code, len(self._session.cookies))
            except Exception as exc:
                logger.warning("Warmup failed %s: %s", url, exc)
            time.sleep(2)
        self._cookies_loaded = True

    def _fetch_chain(self, symbol):
        last_error = None
        for endpoint_tpl in API_ENDPOINTS:
            url = endpoint_tpl.format(symbol=symbol)
            try:
                r = self._session.get(url, timeout=TIMEOUT,
                    headers={**HEADERS, "Referer": "https://www.nseindia.com/option-chain"})
                logger.info("API %s -> HTTP %d  bytes=%d", url, r.status_code, len(r.content))

                if r.status_code == 401:
                    logger.warning("401 — cookies expired, will re-warm")
                    self._cookies_loaded = False
                    return None
                if r.status_code != 200:
                    logger.warning("HTTP %d from %s", r.status_code, url)
                    continue

                raw = r.json()
                if not raw or "records" not in raw:
                    logger.warning("Bad structure from %s  keys=%s", url, list(raw.keys()) if raw else "empty")
                    continue

                parsed = self._parse(raw, symbol)
                if not parsed["strikes"]:
                    logger.warning("0 strikes parsed from %s", url)
                    continue

                logger.info("SUCCESS  spot=%.2f  strikes=%d", parsed["spot"], len(parsed["strikes"]))
                return parsed

            except requests.exceptions.JSONDecodeError as exc:
                logger.warning("JSON error from %s: %s", url, exc)
                last_error = exc
            except requests.exceptions.RequestException as exc:
                logger.warning("Request error from %s: %s", url, exc)
                last_error = exc

        if last_error:
            raise last_error
        return None

    @staticmethod
    def _parse(raw, symbol):
        records      = raw.get("records", {})
        spot         = float(records.get("underlyingValue", 0))
        expiry_dates = records.get("expiryDates", [])
        strikes = []
        for row in records.get("data", []):
            ce = row.get("CE", {})
            pe = row.get("PE", {})
            strikes.append({
                "strike":      int(row.get("strikePrice", 0)),
                "expiry":      row.get("expiryDate", ""),
                "call_oi":     int(ce.get("openInterest",         0)),
                "call_chg_oi": int(ce.get("changeinOpenInterest", 0)),
                "call_volume": int(ce.get("totalTradedVolume",    0)),
                "call_iv":     float(ce.get("impliedVolatility",  0)),
                "call_ltp":    float(ce.get("lastPrice",          0)),
                "put_oi":      int(pe.get("openInterest",         0)),
                "put_chg_oi":  int(pe.get("changeinOpenInterest", 0)),
                "put_volume":  int(pe.get("totalTradedVolume",    0)),
                "put_iv":      float(pe.get("impliedVolatility",  0)),
                "put_ltp":     float(pe.get("lastPrice",          0)),
            })
        return {"symbol": symbol, "spot": spot, "expiry_dates": expiry_dates, "strikes": strikes}
