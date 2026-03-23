"""
PIE TRADER — option_intelligence/service/option_service.py  v3.0  FINAL

ROOT CAUSE FIX:
  Python returned {"error":"No option chain data","symbol":"NIFTY"} because:

  Layer 1 (LIVE mode): live_adapter.fetch() returned None because:
    - Angel WS spot tick (token 26000) not received yet at startup
    - OR token_mapper not loaded (OpenAPIScripMaster.json missing)
    - OR strikes_map empty (option ticks not flowing yet)

  Layer 2 (NSE fallback): nse_option_scraper had hardcoded EXPIRY_MAP with
    expired dates ("24-Mar-2026" on 23-Mar-2026 morning) → NSE API returned
    empty data.

FIX: 3-layer chain resolution:
  1. LIVE adapter (Angel ticks from Java WebSocket)
  2. NSE scraper fallback (dynamic expiry, always current)
  3. Descriptive error with diagnosis for debugging

Other fixes:
  - Passes market_state from MarketStateConsumer to engine.analyze()
  - Implements _store_decision via postgres_client
  - Publishes full contract including smc + intelligence_score to Kafka
"""
from __future__ import annotations

import json
import logging
from typing import Dict, Any, Optional
from datetime import datetime, time as dt_time

from kafka import KafkaProducer

from option_intelligence.ingestion.nse_option_scraper import NSEOptionScraper
from option_intelligence.engine.option_engine import OptionEngine

logger = logging.getLogger(__name__)


class OptionService:

    def __init__(self):
        self.scraper   = NSEOptionScraper()
        self.engine    = OptionEngine()
        self.data_mode = "NSE"

        self.live_adapter            = None
        self.market_state_consumer   = None
        self.postgres_client         = None

        self.producer = self._init_kafka()

    # ── Dependency injection ──────────────────────────────────────────────────

    def set_live_adapter(self, adapter):
        self.live_adapter = adapter
        self.data_mode    = "LIVE"
        logger.info("📡 OptionService → LIVE data mode")

    def set_market_state_consumer(self, consumer):
        self.market_state_consumer = consumer
        # Also wire into the adapter so it can use MarketState for spot fallback
        if self.live_adapter and hasattr(self.live_adapter, "set_market_state_consumer"):
            self.live_adapter.set_market_state_consumer(consumer)
        logger.info("📡 OptionService → MarketStateConsumer wired")

    def set_postgres_client(self, client):
        self.postgres_client = client
        logger.info("🗄️ OptionService → PostgresClient wired")

    # ── Main entry ────────────────────────────────────────────────────────────

    def get_option_summary(self, symbol: str, publish: bool = True) -> Dict[str, Any]:
        try:
            symbol = str(symbol).upper()
            logger.info(f"📊 [{symbol}] Processing option data")

            # ── LAYER 1: LIVE adapter (Angel WebSocket ticks) ─────────────────
            chain = None
            live_error = None

            if self.data_mode == "LIVE" and self.live_adapter:
                try:
                    chain = self.live_adapter.fetch(symbol)
                    if chain:
                        logger.info(
                            f"✅ [{symbol}] LIVE chain: spot={chain.get('spot')} "
                            f"strikes={len(chain.get('strikes', []))}"
                        )
                except Exception as e:
                    live_error = str(e)
                    logger.warning(f"⚠️ [{symbol}] LIVE adapter error: {e}")

            # ── LAYER 2: NSE scraper fallback ─────────────────────────────────
            # Used when: LIVE not set, LIVE returned None, or LIVE threw an error
            if not chain:
                reason = live_error or (
                    "LIVE chain not ready (spot/ticks not flowing yet)"
                    if self.data_mode == "LIVE"
                    else "NSE mode"
                )
                logger.info(f"🔄 [{symbol}] Falling back to NSE scraper ({reason})")
                try:
                    chain = self.scraper.fetch(symbol)
                    if chain:
                        logger.info(
                            f"✅ [{symbol}] NSE chain: spot={chain.get('spot')} "
                            f"strikes={len(chain.get('strikes', []))}"
                        )
                except Exception as e:
                    logger.error(f"❌ [{symbol}] NSE scraper error: {e}")
                    chain = None

            # ── LAYER 3: Fail with diagnosis ──────────────────────────────────
            if not chain:
                msg = (
                    f"No option chain data for {symbol}. "
                    f"Check: 1) Angel WS connected (Java logs) "
                    f"2) token_mapper loaded (angel_feed/OpenAPIScripMaster.json) "
                    f"3) NSE reachable (VPN/network) "
                    f"4) Expiry not expired (next expiry: use /health endpoint)"
                )
                logger.error(f"❌ [{symbol}] {msg}")
                return {"error": "No option chain data", "symbol": symbol, "diagnosis": msg}

            spot         = chain.get("spot")
            rows         = chain.get("strikes", [])
            expiry_dates = chain.get("expiry_dates", [])

            if not spot or not rows:
                return {
                    "error":   "Invalid option chain structure",
                    "symbol":  symbol,
                    "chain":   {"spot": spot, "strikes_count": len(rows)},
                }

            # ── Get Java MarketState for VWAP / PDH / regime ──────────────────
            market_state: Optional[Dict] = None
            if self.market_state_consumer:
                try:
                    market_state = self.market_state_consumer.get(symbol)
                except Exception:
                    pass

            # ── Run intelligence pipeline ─────────────────────────────────────
            result = self.engine.analyze(
                spot,
                rows,
                symbol,
                expiry_dates,
                market_state=market_state,
            )

            # Persist (non-blocking)
            self._store_decision(result)

            # Publish gate: intelligence_score state OR legacy action
            intel  = result.get("intelligence_score", {})
            i_state = intel.get("state", "NO_TRADE")
            action  = (result.get("auto_trade_decision", {})
                            .get("auto_trade_decision", {})
                            .get("action", "NO_TRADE"))

            if publish and self._is_execution_time() and (
                i_state in ("EXECUTE", "READY") or
                action  in ("EXECUTE", "PREPARE")
            ):
                self._publish_to_kafka(result)

            return result

        except Exception as e:
            logger.exception(f"❌ [{symbol}] OptionService failed")
            return {"error": str(e), "symbol": symbol}

    # ── Execution time filter ─────────────────────────────────────────────────

    def _is_execution_time(self) -> bool:
        now = datetime.now().time()
        if now < dt_time(14, 0):    return True
        if now >= dt_time(15, 20):  return True
        return False

    # ── Persist to Postgres ───────────────────────────────────────────────────

    def _store_decision(self, data: Dict[str, Any]):
        if not self.postgres_client:
            return
        try:
            symbol = (data.get("market_context") or {}).get("symbol", "UNKNOWN")
            self.postgres_client.save_analytics_result(symbol, data)
            self.postgres_client.save_regime_log(symbol, data)
            self.postgres_client.save_probability_log(symbol, data)
        except Exception as e:
            logger.error(f"❌ _store_decision failed: {e}")

    # ── Kafka publish ─────────────────────────────────────────────────────────

    def _publish_to_kafka(self, data: Dict[str, Any]):
        if not self.producer:
            return
        try:
            intel  = data.get("intelligence_score", {})
            payload = {
                "symbol":           (data.get("market_context") or {}).get("symbol"),
                "timestamp":        int(datetime.utcnow().timestamp() * 1000),
                "score":            intel.get("total_score", 0),
                "state":            intel.get("state", "NO_TRADE"),
                "action":           intel.get("action", "NO_TRADE"),
                "setup":            intel.get("setup", "NONE"),
                "direction":        intel.get("direction", "NEUTRAL"),
                "score_breakdown":  intel.get("score_breakdown", {}),
                "smc":              self._build_smc_block(data),
                "options": {
                    "gamma_flip":      self._safe(data, ["dealer_positioning", "dealer_inventory_model", "gamma_flip"]),
                    "call_wall":       self._safe(data, ["dealer_positioning", "gamma", "call_gamma_wall"]),
                    "put_wall":        self._safe(data, ["dealer_positioning", "gamma", "put_gamma_wall"]),
                    "dealer_position": self._safe(data, ["dealer_positioning", "dealer_inventory_model", "dealer_inventory"]),
                },
                # Full DTO fields Java AnalyticsConsumer/OptionAnalyticsDTO expects
                "trade_signal":         data.get("trade_signal"),
                "auto_trade_decision":  data.get("auto_trade_decision"),
                "selected_strike":      (data.get("strike_selection") or {}).get("selected_strike"),
                "optimized_strike":     (data.get("strike_optimizer") or {}).get("optimized_strike"),
                "execution_timing":     data.get("execution_timing"),
                "risk_management":      data.get("risk_management"),
                "confidence":           data.get("confidence"),
                "market_context":       data.get("market_context"),
                "dealer_positioning":   data.get("dealer_positioning"),
                "market_structure":     data.get("market_structure"),
                "liquidity_map":        data.get("liquidity_map"),
                "execution_layer":      data.get("execution_layer"),
                "execution_debug":      data.get("execution_debug"),
                "complete_decision":    data.get("complete_decision"),
                "historical_context":   data.get("historical_context"),
                "volatility_context":   data.get("volatility_context"),
                "institutional_flow":   data.get("institutional_flow"),
                "strike_selection":     data.get("strike_selection"),
                "strike_optimizer":     data.get("strike_optimizer"),
                "premium_intelligence": data.get("premium_intelligence"),
                "adaptive_params":      data.get("adaptive_params"),
                "position_management":  data.get("position_management"),
                "scaling":              data.get("scaling"),
                "exit_management":      data.get("exit_management"),
                "ui_view":              data.get("ui_view"),
            }
            self.producer.send("pie.analytics.results", payload)
            self.producer.flush()
            logger.info(
                f"🔥 [{payload.get('symbol')}] → Kafka "
                f"score={payload['score']} state={payload['state']} setup={payload['setup']}"
            )
        except Exception as e:
            logger.error(f"❌ Kafka publish failed: {e}")

    def _build_smc_block(self, data: Dict) -> Dict:
        smc   = data.get("smc_analysis", {})
        inner = smc.get("smc", {})
        ms    = inner.get("market_structure", {})
        liq   = inner.get("liquidity", {})
        ob    = inner.get("order_block", {})
        fvg   = inner.get("fvg", {})
        pd    = inner.get("pd_array", {})
        ote   = inner.get("ote", {})
        amd   = inner.get("amd", {})
        tm    = inner.get("time_model", {})
        bob   = ob.get("bullish_ob") or {}
        beb   = ob.get("bearish_ob") or {}
        return {
            "market_structure":  ms.get("trend", "NEUTRAL"),
            "bos":               ms.get("bos", False),
            "choch":             ms.get("choch", False),
            "liquidity":         liq,
            "order_block": {
                "bearish_ob": [beb.get("zone_low"), beb.get("zone_high")] if beb else None,
                "bullish_ob": [bob.get("zone_low"), bob.get("zone_high")] if bob else None,
            },
            "fvg":       {"zone": fvg.get("bullish_fvg") or fvg.get("bearish_fvg")},
            "pd_array":  {"premium": pd.get("premium"), "discount": pd.get("discount")},
            "ote":       ote.get("zone"),
            "amd_phase": amd.get("phase"),
            "killzone":  tm.get("killzone"),
            "setup":     smc.get("setup", "NONE"),
        }

    def _safe(self, d, keys, default=None):
        try:
            for k in keys:
                d = d.get(k)
                if d is None: return default
            return d
        except Exception:
            return default

    def _init_kafka(self):
        try:
            return KafkaProducer(
                bootstrap_servers="localhost:9092",
                value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
                retries=3, linger_ms=10,
            )
        except Exception as e:
            logger.error(f"❌ Kafka init failed: {e}")
            return None
        