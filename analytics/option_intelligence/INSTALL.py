"""
PIE Trader — Option Intelligence
=================================
Run this script ONCE from inside the option-intelligence folder.
It will overwrite every source file with the correct version.

Usage:
    cd D:\pietrader\analytics\option-intelligence
    python INSTALL.py
"""

import os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))

def write(rel_path, content):
    full = os.path.join(ROOT, rel_path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"  wrote  {rel_path}")

# ─────────────────────────────────────────────────────────────────────────────
# FILE CONTENTS
# ─────────────────────────────────────────────────────────────────────────────

FILES = {}

FILES["config/__init__.py"] = ""

FILES["config/contract_config.py"] = '''
CONTRACT_CONFIG = {
    "NIFTY":     {"lot_size": 75,  "tick_size": 0.05, "strike_gap": 50,  "index_name": "NIFTY 50"},
    "BANKNIFTY": {"lot_size": 30,  "tick_size": 0.05, "strike_gap": 100, "index_name": "NIFTY BANK"},
    "FINNIFTY":  {"lot_size": 40,  "tick_size": 0.05, "strike_gap": 50,  "index_name": "NIFTY FIN SERVICE"},
    "MIDCPNIFTY":{"lot_size": 75,  "tick_size": 0.05, "strike_gap": 25,  "index_name": "NIFTY MIDCAP SELECT"},
}

def get_lot_size(symbol):
    return CONTRACT_CONFIG.get(symbol.upper(), {}).get("lot_size", 50)

def get_strike_gap(symbol):
    return CONTRACT_CONFIG.get(symbol.upper(), {}).get("strike_gap", 50)
'''.lstrip()

FILES["ingestion/__init__.py"] = ""

FILES["ingestion/nse_option_scraper.py"] = '''
from __future__ import annotations
import time, logging, requests

logger = logging.getLogger(__name__)

NSE_HOME        = "https://www.nseindia.com"
NSE_MARKET_DATA = "https://www.nseindia.com/market-data/equity-derivatives-watch"
NSE_OC_API      = "https://www.nseindia.com/api/option-chain-indices?symbol={symbol}"

HEADERS = {
    "User-Agent":      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept":          "application/json, text/plain, */*",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "gzip, deflate, br",
    "Referer":         "https://www.nseindia.com/option-chain",
    "Connection":      "keep-alive",
    "DNT":             "1",
    "Cache-Control":   "no-cache",
    "Pragma":          "no-cache",
    "Sec-Fetch-Dest":  "empty",
    "Sec-Fetch-Mode":  "cors",
    "Sec-Fetch-Site":  "same-origin",
    "X-Requested-With":"XMLHttpRequest",
}

TIMEOUT   = 15
MAX_RETRY = 3


class NSEOptionScraper:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self._cookies_loaded = False

    def fetch(self, symbol="NIFTY"):
        symbol = symbol.upper()
        for attempt in range(1, MAX_RETRY + 1):
            try:
                if not self._cookies_loaded:
                    self._load_cookies()
                raw = self._get_chain(symbol)
                if raw:
                    return self._parse(raw, symbol)
            except requests.exceptions.RequestException as exc:
                logger.warning("Attempt %d/%d failed: %s", attempt, MAX_RETRY, exc)
                self._cookies_loaded = False
                time.sleep(2 * attempt)
        logger.error("All attempts exhausted for %s", symbol)
        return None

    def _load_cookies(self):
        for url in [NSE_HOME, NSE_MARKET_DATA]:
            try:
                self.session.get(url, timeout=TIMEOUT)
                time.sleep(1.5)
            except Exception as exc:
                logger.warning("Cookie page failed %s: %s", url, exc)
        self._cookies_loaded = True

    def _get_chain(self, symbol):
        url = NSE_OC_API.format(symbol=symbol)
        r = self.session.get(url, timeout=TIMEOUT)
        r.raise_for_status()
        data = r.json()
        if not data or "records" not in data:
            return None
        return data

    @staticmethod
    def _parse(raw, symbol):
        records      = raw.get("records", {})
        spot         = float(records.get("underlyingValue", 0))
        expiry_dates = records.get("expiryDates", [])
        raw_data     = records.get("data", [])
        strikes = []
        for row in raw_data:
            ce = row.get("CE", {})
            pe = row.get("PE", {})
            strikes.append({
                "strike":      int(row.get("strikePrice", 0)),
                "expiry":      row.get("expiryDate", ""),
                "call_oi":     int(ce.get("openInterest", 0)),
                "call_chg_oi": int(ce.get("changeinOpenInterest", 0)),
                "call_volume": int(ce.get("totalTradedVolume", 0)),
                "call_iv":     float(ce.get("impliedVolatility", 0)),
                "call_ltp":    float(ce.get("lastPrice", 0)),
                "put_oi":      int(pe.get("openInterest", 0)),
                "put_chg_oi":  int(pe.get("changeinOpenInterest", 0)),
                "put_volume":  int(pe.get("totalTradedVolume", 0)),
                "put_iv":      float(pe.get("impliedVolatility", 0)),
                "put_ltp":     float(pe.get("lastPrice", 0)),
            })
        return {"symbol": symbol, "spot": spot, "expiry_dates": expiry_dates, "strikes": strikes}
'''.lstrip()

FILES["engine/__init__.py"] = ""

FILES["engine/atm_engine.py"] = '''
from __future__ import annotations
from option_intelligence.config.contract_config import get_strike_gap

def get_atm_data(spot, strikes, symbol="NIFTY", window=15):
    gap  = get_strike_gap(symbol)
    atm  = round(spot / gap) * gap
    all_strikes = sorted({s["strike"] for s in strikes})
    try:
        idx = all_strikes.index(atm)
    except ValueError:
        atm = min(all_strikes, key=lambda x: abs(x - spot))
        idx = all_strikes.index(atm)
    lo = max(0, idx - window)
    hi = min(len(all_strikes) - 1, idx + window)
    return {"atm": atm, "atm_window": all_strikes[lo:hi + 1]}
'''.lstrip()

FILES["engine/pcr_engine.py"] = '''
from __future__ import annotations

def get_pcr_data(strikes, atm_window=None):
    filtered = [s for s in strikes if atm_window is None or s["strike"] in atm_window]
    total_call_oi = sum(s["call_oi"] for s in filtered)
    total_put_oi  = sum(s["put_oi"]  for s in filtered)
    pcr = round(total_put_oi / total_call_oi, 4) if total_call_oi else 0.0
    if pcr >= 1.2:   sentiment = "BULLISH"
    elif pcr <= 0.8: sentiment = "BEARISH"
    else:            sentiment = "NEUTRAL"
    return {"pcr": pcr, "sentiment": sentiment, "total_call_oi": total_call_oi, "total_put_oi": total_put_oi}
'''.lstrip()

FILES["engine/gamma_engine.py"] = '''
from __future__ import annotations
import math, logging

logger = logging.getLogger(__name__)
RISK_FREE_RATE = 0.065

def _norm_pdf(x):
    return math.exp(-0.5 * x * x) / math.sqrt(2 * math.pi)

def _bs_gamma(spot, strike, T, iv, r=RISK_FREE_RATE):
    if T <= 0 or iv <= 0 or spot <= 0 or strike <= 0:
        return 0.0
    try:
        d1 = (math.log(spot / strike) + (r + 0.5 * iv ** 2) * T) / (iv * math.sqrt(T))
        return _norm_pdf(d1) / (spot * iv * math.sqrt(T))
    except (ZeroDivisionError, ValueError):
        return 0.0

def get_gamma_data(strikes, spot, tte_years, atm_window=None):
    filtered = [s for s in strikes if atm_window is None or s["strike"] in atm_window]
    best_call = {"strike": 0, "gamma": 0.0, "oi": 1}
    best_put  = {"strike": 0, "gamma": 0.0, "oi": 1}
    enriched  = []
    for row in filtered:
        K    = row["strike"]
        c_iv = (row.get("call_iv", 0) or 15.0) / 100.0
        p_iv = (row.get("put_iv",  0) or 15.0) / 100.0
        c_g  = _bs_gamma(spot, K, tte_years, c_iv)
        p_g  = _bs_gamma(spot, K, tte_years, p_iv)
        r2   = dict(row); r2["call_gamma"] = round(c_g, 8); r2["put_gamma"] = round(p_g, 8)
        enriched.append(r2)
        if c_g * row["call_oi"] > best_call["gamma"] * best_call["oi"]:
            best_call = {"strike": K, "gamma": c_g, "oi": max(row["call_oi"], 1)}
        if p_g * row["put_oi"] > best_put["gamma"] * best_put["oi"]:
            best_put  = {"strike": K, "gamma": p_g, "oi": max(row["put_oi"],  1)}
    call_wall  = best_call["strike"]
    put_wall   = best_put["strike"]
    gamma_flip = round((call_wall + put_wall) / 2 / 50) * 50
    return {"call_gamma_wall": call_wall, "put_gamma_wall": put_wall, "gamma_flip": gamma_flip, "strikes_enriched": enriched}
'''.lstrip()

FILES["engine/gex_engine.py"] = '''
from __future__ import annotations
from option_intelligence.config.contract_config import get_lot_size

def get_gex_data(strikes, spot, symbol="NIFTY", atm_window=None):
    lot      = get_lot_size(symbol)
    scale    = spot ** 2 * 0.01
    filtered = [s for s in strikes if atm_window is None or s["strike"] in atm_window]
    total_gex = 0.0
    levels    = []
    for row in filtered:
        c_g  = row.get("call_gamma", 0.01)
        p_g  = row.get("put_gamma",  0.01)
        net  = (c_g * row.get("call_oi", 0) - p_g * row.get("put_oi", 0)) * lot * scale
        total_gex += net
        levels.append({"strike": row["strike"], "gex": round(net, 2)})
    levels.sort(key=lambda x: abs(x["gex"]), reverse=True)
    return {"total_gex": round(total_gex, 2), "gamma_regime": "positive_gamma" if total_gex >= 0 else "negative_gamma", "levels": levels[:10]}
'''.lstrip()

FILES["engine/ladder_engine.py"] = '''
from __future__ import annotations

def get_ladder_data(strikes, spot, top_n=5):
    call_sorted = sorted(strikes, key=lambda x: x["call_oi"], reverse=True)
    put_sorted  = sorted(strikes, key=lambda x: x["put_oi"],  reverse=True)
    resistance  = [{"strike": s["strike"], "call_oi": s["call_oi"]} for s in call_sorted[:top_n] if s["strike"] >= spot]
    support     = [{"strike": s["strike"], "put_oi":  s["put_oi"]}  for s in put_sorted[:top_n]  if s["strike"] <= spot]
    return {
        "call_resistance_zones": sorted(resistance, key=lambda x: x["strike"]),
        "put_support_zones":     sorted(support,    key=lambda x: x["strike"], reverse=True),
    }
'''.lstrip()

FILES["engine/liquidity_engine.py"] = '''
from __future__ import annotations

def get_liquidity_data(strikes, spot):
    above = [s for s in strikes if s["strike"] > spot]
    below = [s for s in strikes if s["strike"] < spot]
    resistance = max(above, key=lambda x: x["call_oi"], default={}).get("strike", 0)
    support    = max(below, key=lambda x: x["put_oi"],  default={}).get("strike", 0)
    return {"support": support, "resistance": resistance}
'''.lstrip()

FILES["engine/volatility_engine.py"] = '''
from __future__ import annotations
import math

def get_volatility_data(strikes, spot, atm):
    rows = [s for s in strikes if s["strike"] == atm] or sorted(strikes, key=lambda x: abs(x["strike"] - spot))
    row  = rows[0]
    c_iv = row.get("call_iv", 0)
    p_iv = row.get("put_iv",  0)
    iv   = (c_iv + p_iv) / 2.0 if (c_iv + p_iv) > 0 else 15.0
    iv_f = iv / 100.0
    daily  = spot * iv_f / math.sqrt(252)
    weekly = spot * iv_f / math.sqrt(52)
    return {
        "atm_iv_pct": round(iv, 2),
        "daily_move": round(daily, 2), "weekly_move": round(weekly, 2),
        "upper_1d": round(spot + daily,  2), "lower_1d": round(spot - daily,  2),
        "upper_1w": round(spot + weekly, 2), "lower_1w": round(spot - weekly, 2),
    }
'''.lstrip()

FILES["engine/pressure_engine.py"] = '''
from __future__ import annotations

def get_pressure_data(strikes, spot, atm_window):
    f        = [s for s in strikes if s["strike"] in atm_window]
    c_oi     = sum(s["call_oi"]    for s in f if s["strike"] >= spot)
    p_oi     = sum(s["put_oi"]     for s in f if s["strike"] <= spot)
    c_vol    = sum(s["call_volume"] for s in f)
    p_vol    = sum(s["put_volume"]  for s in f)
    oi_t     = c_oi  + p_oi  or 1
    vol_t    = c_vol + p_vol or 1
    pressure = round(((p_oi - c_oi) / oi_t * 60) + ((p_vol - c_vol) / vol_t * 40), 2)
    pressure = max(-100, min(100, pressure))
    if   pressure >  50: label = "STRONG_BULLISH"
    elif pressure >  20: label = "BULLISH"
    elif pressure < -50: label = "STRONG_BEARISH"
    elif pressure < -20: label = "BEARISH"
    else:                label = "NEUTRAL"
    return {"pressure": pressure, "label": label}
'''.lstrip()

FILES["engine/compression_engine.py"] = '''
from __future__ import annotations

def get_compression_data(call_wall, put_wall, gamma_flip, spot):
    wall_pct = abs(call_wall - put_wall) / spot * 100
    flip_pct = abs(spot - gamma_flip)   / spot * 100
    compressed = wall_pct < 1.0 and flip_pct < 0.5
    if compressed:     signal = "IMMINENT_BREAKOUT"
    elif wall_pct < 2: signal = "COMPRESSION_BUILDING"
    else:              signal = "NO_COMPRESSION"
    return {"compression_detected": compressed, "wall_spread_pct": round(wall_pct, 3), "flip_distance_pct": round(flip_pct, 3), "breakout_signal": signal}
'''.lstrip()

FILES["engine/expiry_engine.py"] = '''
from __future__ import annotations
from datetime import datetime, date

def get_expiry_data(expiry_dates):
    today  = date.today()
    parsed = []
    for d in expiry_dates:
        for fmt in ("%d-%b-%Y", "%Y-%m-%d", "%d/%m/%Y"):
            try:
                parsed.append(datetime.strptime(d, fmt).date()); break
            except ValueError:
                pass
    if not parsed:
        return {"nearest_expiry": "UNKNOWN", "days_to_expiry": -1, "phase": "UNKNOWN", "tte_years": 0.02}
    nearest = min((x for x in parsed if x >= today), default=min(parsed), key=lambda x: abs((x - today).days))
    dte = max((nearest - today).days, 0)
    if   dte == 0:  phase = "EXPIRY_DAY"
    elif dte <= 2:  phase = "EXPIRY_EVE"
    elif dte <= 7:  phase = "EXPIRY_WEEK"
    elif dte <= 14: phase = "POSITIONING_PHASE"
    else:           phase = "EARLY_CYCLE"
    return {"nearest_expiry": str(nearest), "days_to_expiry": dte, "phase": phase, "tte_years": round(max(dte, 1) / 365.0, 6)}
'''.lstrip()

FILES["engine/session_engine.py"] = '''
from __future__ import annotations
from datetime import datetime, time, timezone, timedelta

IST          = timezone(timedelta(hours=5, minutes=30))
MARKET_OPEN  = time(9, 15)
MARKET_CLOSE = time(15, 30)

def get_session_data():
    now = datetime.now(IST).time()
    if   now < MARKET_OPEN:     session = "PRE_MARKET"
    elif now > MARKET_CLOSE:    session = "POST_MARKET"
    elif now <= time(10, 30):   session = "OPENING_SESSION"
    elif now <= time(13, 30):   session = "MID_SESSION"
    elif now <= time(14, 30):   session = "AFTERNOON_SESSION"
    else:                       session = "CLOSING_SESSION"
    return {"session": session, "time_ist": datetime.now(IST).strftime("%H:%M:%S"), "is_market": MARKET_OPEN <= now <= MARKET_CLOSE}
'''.lstrip()

FILES["engine/oi_flow_engine.py"] = '''
from __future__ import annotations

_prev = {}

def update_snapshot(strikes):
    global _prev
    _prev = {str(s["strike"]): s for s in strikes}

def get_oi_flow_data(strikes, spot):
    flows = []
    for s in strikes:
        prev = _prev.get(str(s["strike"]))
        if not prev:
            continue
        c_chg = s["call_oi"] - prev["call_oi"]
        p_chg = s["put_oi"]  - prev["put_oi"]
        if s["strike"] > spot:
            sig = "CALL_OI_BUILD" if c_chg > 0 else "CALL_OI_UNWIND" if c_chg < 0 else None
        else:
            sig = "PUT_OI_BUILD"  if p_chg > 0 else "PUT_OI_UNWIND"  if p_chg < 0 else None
        if sig:
            flows.append({"strike": s["strike"], "signal": sig, "call_chg": c_chg, "put_chg": p_chg})
    return {"oi_flows": flows[:10]}
'''.lstrip()

FILES["engine/probability_engine.py"] = '''
from __future__ import annotations

def get_probability_data(spot, gamma_flip, pcr, gamma_regime, pressure=0.0):
    score = 0
    if spot > gamma_flip:       score += 2
    elif spot < gamma_flip:     score -= 2
    if gamma_regime == "negative_gamma": score += 1
    else:                                score -= 1
    if pcr >= 1.2:   score += 1
    elif pcr <= 0.8: score -= 1
    if pressure > 30:   score += 1
    elif pressure < -30: score -= 1
    if   score >= 3:  direction = "STRONG_UPSIDE"
    elif score >= 1:  direction = "UPSIDE_BIAS"
    elif score <= -3: direction = "STRONG_DOWNSIDE"
    elif score <= -1: direction = "DOWNSIDE_BIAS"
    else:             direction = "NEUTRAL"
    return {"direction": direction, "score": score, "gamma_flip": gamma_flip, "above_flip": spot > gamma_flip}
'''.lstrip()

FILES["engine/option_engine.py"] = '''
from __future__ import annotations
import logging
from option_intelligence.engine.atm_engine         import get_atm_data
from option_intelligence.engine.pcr_engine         import get_pcr_data
from option_intelligence.engine.gamma_engine       import get_gamma_data
from option_intelligence.engine.gex_engine         import get_gex_data
from option_intelligence.engine.ladder_engine      import get_ladder_data
from option_intelligence.engine.liquidity_engine   import get_liquidity_data
from option_intelligence.engine.volatility_engine  import get_volatility_data
from option_intelligence.engine.pressure_engine    import get_pressure_data
from option_intelligence.engine.compression_engine import get_compression_data
from option_intelligence.engine.probability_engine import get_probability_data
from option_intelligence.engine.expiry_engine      import get_expiry_data
from option_intelligence.engine.session_engine     import get_session_data
from option_intelligence.engine.oi_flow_engine     import get_oi_flow_data, update_snapshot

logger = logging.getLogger(__name__)


class OptionEngine:
    def __init__(self, symbol="NIFTY"):
        self.symbol = symbol.upper()

    def run(self, chain):
        symbol  = chain["symbol"]
        spot    = chain["spot"]
        strikes = chain["strikes"]
        if not strikes or spot == 0:
            return {"error": "empty_chain", "symbol": symbol}

        expiry     = get_expiry_data(chain.get("expiry_dates", []))
        tte        = max(expiry["tte_years"], 1 / 365)
        atm_data   = get_atm_data(spot, strikes, symbol)
        atm        = atm_data["atm"]
        atm_window = atm_data["atm_window"]

        pcr_data   = get_pcr_data(strikes, atm_window)

        gamma_data = get_gamma_data(strikes, spot, tte, atm_window)
        enriched   = gamma_data.pop("strikes_enriched")
        call_wall  = gamma_data["call_gamma_wall"]
        put_wall   = gamma_data["put_gamma_wall"]
        gamma_flip = gamma_data["gamma_flip"]

        gex_data      = get_gex_data(enriched, spot, symbol, atm_window)
        gamma_regime  = gex_data["gamma_regime"]

        ladder_data    = get_ladder_data(strikes, spot)
        liquidity_data = get_liquidity_data(strikes, spot)
        vol_data       = get_volatility_data(strikes, spot, atm)
        pressure_data  = get_pressure_data(strikes, spot, atm_window)
        compression_data = get_compression_data(call_wall, put_wall, gamma_flip, spot)
        prob_data      = get_probability_data(spot, gamma_flip, pcr_data["pcr"], gamma_regime, pressure_data["pressure"])
        session_data   = get_session_data()
        oi_flow_data   = get_oi_flow_data(strikes, spot)
        update_snapshot(strikes)

        return {
            "symbol": symbol, "spot": spot, "atm": atm,
            "expiry": expiry, "session": session_data,
            "pcr": pcr_data, "gamma": gamma_data, "gex": gex_data,
            "ladder": ladder_data, "liquidity": liquidity_data,
            "volatility": vol_data, "pressure": pressure_data,
            "compression": compression_data, "probability": prob_data,
            "oi_flow": oi_flow_data,
        }
'''.lstrip()

FILES["service/__init__.py"] = ""

FILES["service/option_service.py"] = '''
from __future__ import annotations
import time, logging
from option_intelligence.ingestion.nse_option_scraper import NSEOptionScraper
from option_intelligence.engine.option_engine          import OptionEngine

logger  = logging.getLogger(__name__)
CACHE_TTL = 15


class OptionService:
    def __init__(self):
        self._scraper = NSEOptionScraper()
        self._cache   = {}

    def get_summary(self, symbol="NIFTY"):
        symbol = symbol.upper()
        cached = self._cache.get(symbol)
        if cached and (time.time() - cached["ts"]) < CACHE_TTL:
            return {**cached["data"], "_cached": True}
        try:
            chain = self._scraper.fetch(symbol)
        except Exception as exc:
            return {"error": str(exc), "symbol": symbol}
        if chain is None:
            return {"error": "No option chain data returned from NSE", "symbol": symbol}
        if not chain.get("strikes"):
            return {"error": "Option chain has no strikes", "symbol": symbol}
        try:
            summary = OptionEngine(symbol).run(chain)
        except Exception as exc:
            logger.exception("Engine failed: %s", exc)
            return {"error": str(exc), "symbol": symbol}
        self._cache[symbol] = {"ts": time.time(), "data": summary}
        return {**summary, "_cached": False}
'''.lstrip()

FILES["app.py"] = '''
from __future__ import annotations
import logging, sys
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from from option_intelligence.service.option_service import OptionService

logging.basicConfig(stream=sys.stdout, level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s", datefmt="%H:%M:%S")

app = FastAPI(title="PIE Trader — Option Intelligence", version="2.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

_svc = OptionService()
VALID = {"NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"}

def _get(symbol):
    s = symbol.upper()
    if s not in VALID:
        return JSONResponse(status_code=400, content={"error": f"Unknown symbol {s}"})
    data = _svc.get_summary(s)
    if "error" in data:
        return JSONResponse(status_code=502, content=data)
    return data

@app.get("/")
def health():
    return {"status": "ok", "version": "2.0.0"}

@app.get("/option-summary")
def option_summary(symbol: str = Query(default="NIFTY")):
    return _get(symbol)

@app.get("/pcr")
def pcr(symbol: str = Query(default="NIFTY")):
    d = _get(symbol)
    return d if isinstance(d, JSONResponse) else {"symbol": symbol, "spot": d["spot"], "pcr": d["pcr"], "session": d["session"]}

@app.get("/gex")
def gex(symbol: str = Query(default="NIFTY")):
    d = _get(symbol)
    return d if isinstance(d, JSONResponse) else {"symbol": symbol, "spot": d["spot"], "gex": d["gex"], "gamma": d["gamma"]}

@app.get("/levels")
def levels(symbol: str = Query(default="NIFTY")):
    d = _get(symbol)
    return d if isinstance(d, JSONResponse) else {"symbol": symbol, "spot": d["spot"], "atm": d["atm"], "liquidity": d["liquidity"], "ladder": d["ladder"], "volatility": d["volatility"]}

@app.get("/probability")
def probability(symbol: str = Query(default="NIFTY")):
    d = _get(symbol)
    return d if isinstance(d, JSONResponse) else {"symbol": symbol, "spot": d["spot"], "probability": d["probability"], "pressure": d["pressure"], "pcr": d["pcr"]}

@app.get("/oi-flow")
def oi_flow(symbol: str = Query(default="NIFTY")):
    d = _get(symbol)
    return d if isinstance(d, JSONResponse) else {"symbol": symbol, "spot": d["spot"], "oi_flow": d["oi_flow"]}
'''.lstrip()

FILES["requirements.txt"] = "fastapi>=0.110.0\nuvicorn[standard]>=0.29.0\nrequests>=2.31.0\n"

FILES["test_scraper.py"] = '''
import sys, json, logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
sys.path.insert(0, ".")
from option_intelligence.ingestion.nse_option_scraper import NSEOptionScraper

print("\\nFetching NIFTY option chain...\\n")
data = NSEOptionScraper().fetch("NIFTY")
if data is None:
    print("FAILED — check network / VPN (NSE may block non-Indian IPs)")
    sys.exit(1)
print(f"SUCCESS  spot={data[\'spot\']}  strikes={len(data[\'strikes\'])}  expiries={data[\'expiry_dates\'][:2]}")
print(json.dumps(data["strikes"][0], indent=2))
'''.lstrip()

# ─────────────────────────────────────────────────────────────────────────────
# WRITE ALL FILES
# ─────────────────────────────────────────────────────────────────────────────

print(f"\nInstalling to: {ROOT}\n")
for rel, content in FILES.items():
    write(rel, content)

# ─────────────────────────────────────────────────────────────────────────────
# VERIFY SYNTAX
# ─────────────────────────────────────────────────────────────────────────────
import ast, glob
print("\nVerifying syntax...")
ok = True
for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.py"), recursive=True)):
    rel = os.path.relpath(path, ROOT)
    if rel == "INSTALL.py":
        continue
    try:
        ast.parse(open(path).read())
        print(f"  OK  {rel}")
    except SyntaxError as e:
        print(f"  !! SYNTAX ERROR  {rel}: {e}")
        ok = False

print()
if ok:
    print("All files written and verified.")
    print("\nNext steps:")
    print("  pip install fastapi uvicorn requests")
    print("  uvicorn app:app --reload")
else:
    print("Some files have errors — please report back.")
