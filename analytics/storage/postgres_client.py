"""
PIE TRADER — storage/postgres_client.py
Production Postgres client.

Writes:
  - option_chain_snapshots  (every 15 min)
  - analytics_result        (every analytics cycle)
  - regime_log              (regime + context per cycle)
  - probability_log         (win probability per cycle)

Connection: lazy — does NOT connect at import time.
Auto-reconnect on broken connection.
"""

from __future__ import annotations
import logging
import json
from typing import Optional

try:
    import psycopg2
    from psycopg2.extras import execute_batch, Json
except ImportError:
    raise ImportError(
        "\n\n❌ psycopg2 not installed.\n"
        "Run:  pip install psycopg2-binary\n"
    )

from config.settings import Settings

logger = logging.getLogger(__name__)


class PostgresClient:
    """
    Thread-safe Postgres client with lazy connection and auto-reconnect.
    One instance is shared across all services (singleton in app.py).
    """

    def __init__(self):
        self._conn = None   # Lazy — do NOT connect in __init__

    # ── Connection management ─────────────────────────────────────────────────

    def _get_conn(self):
        """Return a live connection, reconnecting if dropped."""
        if self._conn is None or self._conn.closed:
            try:
                self._conn = psycopg2.connect(Settings.POSTGRES_URL)
                self._conn.autocommit = True
                logger.info("✅ Postgres connected")
            except Exception as e:
                logger.error(f"❌ Postgres connection failed: {e}")
                self._conn = None
                raise
        return self._conn

    def is_healthy(self) -> bool:
        """Returns True if Postgres is reachable."""
        try:
            conn = self._get_conn()
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
            return True
        except Exception:
            return False

    # ── Option chain snapshot ────────────────────────────────────────────────

    def save_option_chain_snapshot(self, snapshot_rows: list):
        """
        Persist option chain rows to option_chain_snapshots table.
        snapshot_rows: list of tuples (token, ltp, bid, ask, bid_qty, ask_qty, volume, oi, timestamp_epoch)
        """
        if not snapshot_rows:
            return
        query = """
        INSERT INTO option_chain_snapshots
            (token, ltp, bid, ask, bid_qty, ask_qty, volume, oi, timestamp)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, to_timestamp(%s))
        ON CONFLICT DO NOTHING
        """
        try:
            conn = self._get_conn()
            with conn.cursor() as cur:
                execute_batch(cur, query, snapshot_rows, page_size=500)
            logger.debug(f"📸 Snapshot saved: {len(snapshot_rows)} rows")
        except Exception as e:
            logger.error(f"❌ Snapshot save failed: {e}")
            self._conn = None   # Force reconnect on next call

    # ── Analytics result ─────────────────────────────────────────────────────

    def save_analytics_result(self, symbol: str, result: dict):
        """
        Persist full analytics DTO to analytics_result table.
        Stores the raw JSON so AI training can replay any field.
        """
        query = """
        INSERT INTO analytics_result
            (symbol, action, confidence, regime, spot, recorded_at, raw_json)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        """
        try:
            action     = _safe_get(result, ["auto_trade_decision", "auto_trade_decision", "action"], "?")
            confidence = _safe_get(result, ["confidence", "confidence_score"], 0)
            regime     = _safe_get(result, ["market_context", "regime"], "UNKNOWN")
            spot       = _safe_get(result, ["market_context", "spot"], 0.0)
            import time
            conn = self._get_conn()
            with conn.cursor() as cur:
                cur.execute(query, (
                    symbol, action, confidence, regime, spot,
                    int(time.time() * 1000),
                    json.dumps(result, default=str)
                ))
            logger.debug(f"📊 Analytics result saved → {symbol} action={action}")
        except Exception as e:
            logger.error(f"❌ Analytics result save failed: {e}")
            self._conn = None

    # ── Regime log ───────────────────────────────────────────────────────────

    def save_regime_log(self, symbol: str, result: dict):
        """Log regime + key market context for ML feature store."""
        query = """
        INSERT INTO regime_log
            (symbol, regime, iv_regime, pcr, spot, gamma_flip, fear_index, session_phase, recorded_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        try:
            import time
            conn = self._get_conn()
            with conn.cursor() as cur:
                cur.execute(query, (
                    symbol,
                    _safe_get(result, ["market_context", "regime"],                       "UNKNOWN"),
                    _safe_get(result, ["volatility_context", "iv_regime"],                "UNKNOWN"),
                    _safe_get(result, ["volatility_context", "pcr", "pcr_value"],         None),
                    _safe_get(result, ["market_context", "spot"],                         None),
                    _safe_get(result, ["dealer_positioning", "dealer_inventory_model",
                                       "gamma_flip"],                                     None),
                    _safe_get(result, ["complete_decision", "fear_index_analysis",
                                       "current_fear_index"],                             None),
                    _safe_get(result, ["market_context", "session", "session_phase"],     None),
                    int(time.time() * 1000),
                ))
        except Exception as e:
            logger.error(f"❌ Regime log save failed: {e}")
            self._conn = None

    # ── Probability log ──────────────────────────────────────────────────────

    def save_probability_log(self, symbol: str, result: dict):
        """Log win probability per cycle for model evaluation."""
        query = """
        INSERT INTO probability_log
            (symbol, win_probability, confidence, direction, recorded_at)
        VALUES (%s, %s, %s, %s, %s)
        """
        try:
            import time
            conn = self._get_conn()
            with conn.cursor() as cur:
                cur.execute(query, (
                    symbol,
                    _safe_get(result, ["probability_matrix", "win_probability"], None),
                    _safe_get(result, ["confidence", "confidence_score"],        None),
                    _safe_get(result, ["auto_trade_decision", "auto_trade_decision",
                                       "direction"],                             None),
                    int(time.time() * 1000),
                ))
        except Exception as e:
            logger.error(f"❌ Probability log save failed: {e}")
            self._conn = None

    # ── Market state snapshot ────────────────────────────────────────────────

    def save_market_state(self, symbol: str, state: dict):
        """Persist raw market state snapshot for replay / backtesting."""
        query = """
        INSERT INTO market_state
            (symbol, spot, atm, iv, pcr, regime, recorded_at, raw_json)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT DO NOTHING
        """
        try:
            import time
            conn = self._get_conn()
            with conn.cursor() as cur:
                cur.execute(query, (
                    symbol,
                    state.get("spot"),
                    state.get("atm"),
                    state.get("iv"),
                    state.get("pcr"),
                    state.get("regime", "UNKNOWN"),
                    int(time.time() * 1000),
                    json.dumps(state, default=str),
                ))
        except Exception as e:
            logger.error(f"❌ Market state save failed: {e}")
            self._conn = None


# ── Private helpers ───────────────────────────────────────────────────────────

def _safe_get(d: dict, keys: list, default=None):
    """Safe nested dict getter — never raises."""
    try:
        for k in keys:
            if not isinstance(d, dict):
                return default
            d = d.get(k)
            if d is None:
                return default
        return d
    except Exception:
        return default
