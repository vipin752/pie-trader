import requests
import urllib3
import time

urllib3.disable_warnings()

BASE = "https://www.nseindia.com"
OPTION_PAGE = "https://www.nseindia.com/option-chain"

API = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol={symbol}&expiry={expiry}"

HEADERS = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
    "Referer": OPTION_PAGE
}

EXPIRY_MAP = {
    "NIFTY": "24-Mar-2026",
    "BANKNIFTY": "30-Mar-2026",
    "MIDCPNIFTY": "30-Mar-2026",
    "FINNIFTY": "30-Mar-2026"
}


class NSEOptionScraper:

    def __init__(self):

        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update(HEADERS)

        self._warmup()

    def _warmup(self):

        self.session.get(BASE)
        time.sleep(1)

        self.session.get(OPTION_PAGE)
        time.sleep(1)

    def fetch(self, symbol):

        symbol = symbol.upper()

        expiry = EXPIRY_MAP.get(symbol)

        if not expiry:
            raise Exception(f"Unsupported symbol {symbol}")

        url = API.format(symbol=symbol, expiry=expiry)

        r = self.session.get(url)

        if r.status_code != 200:
            raise Exception("NSE request failed")

        raw = r.json()

        records = raw.get("records", {})

        spot = float(records.get("underlyingValue", 0))

        expiry_dates = records.get("expiryDates", [])

        strikes = []

        for row in records.get("data", []):

            ce = row.get("CE", {})
            pe = row.get("PE", {})

            strikes.append({

                "strike": row.get("strikePrice", 0),

                "call_oi": ce.get("openInterest", 0),
                "put_oi": pe.get("openInterest", 0),

                "call_volume": ce.get("totalTradedVolume", 0),
                "put_volume": pe.get("totalTradedVolume", 0),

                "call_iv": ce.get("impliedVolatility", 0),
                "put_iv": pe.get("impliedVolatility", 0),

                "call_ltp": ce.get("lastPrice", 0),
                "put_ltp": pe.get("lastPrice", 0)
            })

        return {
            "symbol": symbol,
            "spot": spot,
            "expiry_dates": expiry_dates,
            "strikes": strikes
        }