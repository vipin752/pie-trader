"""
PIE TRADER — option_intelligence/engine/timeframe_engine.py

Multi-Timeframe Analysis Engine.

Professional trading hierarchy (from document):
  Monthly  → Bias direction
  Weekly   → Setup direction
  Daily    → Structure (PDH/PDL, Inside Day, Range)
  Intraday → Entry timing

Since Python receives only option chain data (no OHLCV bars), timeframes
are derived from:
  - historical_context  : regime + avg_pcr → Monthly/Weekly proxy
  - compression data    : range width + structure → Daily proxy
  - VWAP + pressure     : real-time session → Intraday
  - Java MarketState    : PDH/PDL/VWAP/regime when available (pie.market.state)

When Java MarketStateDTO provides pdh/pdl/weekly_high/weekly_low (future),
those exact values are used directly. Current implementation derives them
from available option chain signals.

Output (from document contract):
{
  "timeframe_analysis": {
    "monthly":  { "trend": "UP",  "structure": "HH_HL", "key_resistance": 23200, "key_support": 21800 },
    "weekly":   { "trend": "UP",  "structure": "HH_HL", "swing_high": 23150, "swing_low": 22200 },
    "daily":    { "trend": "SIDEWAYS", "pdh": 22680, "pdl": 22490, "day_structure": "INSIDE_DAY" },
    "intraday": { "trend": "DOWN", "vwap_bias": "BELOW", "structure": "LL_LH" }
  },
  "alignment": {
    "monthly_weekly": bool,
    "weekly_daily":   bool,
    "daily_intraday": bool,
    "all_aligned":    bool,
    "alignment_score": int,    # 0-15 points for confidence scoring
    "trade_bias":     str,     # STRONG_LONG | STRONG_SHORT | INTRADAY_LONG | INTRADAY_SHORT | RANGE
    "direction_filter": str    # UP | DOWN | RANGE
  }
}
"""

from typing import Dict, Any, Optional


class TimeframeEngine:

    def analyze(self,
                spot: float,
                data: Dict[str, Any],
                market_state: Optional[Dict] = None) -> Dict[str, Any]:
        """
        Derives multi-timeframe structure from available data.

        Args:
            spot:         Current spot price
            data:         Full analytics context (dealer, compression, vwap, pressure etc.)
            market_state: Optional Java MarketState (provides PDH/PDL/weekly levels)
        """
        try:
            monthly  = self._monthly(data, market_state)
            weekly   = self._weekly(data, market_state, spot)
            daily    = self._daily(data, market_state, spot)
            intraday = self._intraday(data, spot)
            alignment = self._alignment(monthly, weekly, daily, intraday)

            return {
                "timeframe_analysis": {
                    "monthly":  monthly,
                    "weekly":   weekly,
                    "daily":    daily,
                    "intraday": intraday,
                },
                "alignment": alignment,
            }

        except Exception as e:
            return {
                "timeframe_analysis": {
                    "monthly":  {"trend": "UNKNOWN"},
                    "weekly":   {"trend": "UNKNOWN"},
                    "daily":    {"trend": "UNKNOWN"},
                    "intraday": {"trend": "UNKNOWN"},
                },
                "alignment": {
                    "alignment_score": 0,
                    "trade_bias": "RANGE",
                    "direction_filter": "RANGE",
                    "error": str(e),
                },
            }

    # ─────────────────────────────────────────────────────────────────────────
    # MONTHLY — derived from historical regime + PCR trend
    # ─────────────────────────────────────────────────────────────────────────

    def _monthly(self, data: Dict, market_state: Optional[Dict]) -> Dict:
        historical = data.get("historical_context", {})
        regime     = historical.get("regime", "")
        avg_pcr    = historical.get("avg_pcr", 1.0)

        # Monthly trend from historical regime
        if "BULLISH" in regime.upper():
            trend = "UP"
            structure = "HH_HL"
        elif "BEARISH" in regime.upper():
            trend = "DOWN"
            structure = "LL_LH"
        elif "BALANCED" in regime.upper() or "NEUTRAL" in regime.upper():
            trend = "SIDEWAYS"
            structure = "RANGE"
        else:
            # Fall back to PCR: high PCR = bullish positioning
            trend = "UP" if avg_pcr > 1.1 else "DOWN" if avg_pcr < 0.9 else "SIDEWAYS"
            structure = "HH_HL" if trend == "UP" else "LL_LH" if trend == "DOWN" else "RANGE"

        # Key levels from liquidity
        liq = data.get("liquidity_map", {}).get("support_resistance", {})
        support    = liq.get("support", 0)
        resistance = liq.get("resistance", 0)

        # Monthly key levels are typically 5-10% wider than intraday
        key_support    = int(support * 0.97)    if support    else 0
        key_resistance = int(resistance * 1.03) if resistance else 0

        return {
            "trend":           trend,
            "structure":       structure,
            "key_resistance":  key_resistance,
            "key_support":     key_support,
            "regime":          regime,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # WEEKLY — derived from compression width + smart money flow
    # ─────────────────────────────────────────────────────────────────────────

    def _weekly(self, data: Dict, market_state: Optional[Dict], spot: float) -> Dict:
        compression = data.get("market_structure", {}).get("compression", {})
        smart_money = data.get("institutional_flow", {}).get("smart_money_flow_engine", {})
        dealer      = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        liq         = data.get("liquidity_map", {}).get("support_resistance", {})

        # Java market state provides weekly levels if available
        weekly_high = 0
        weekly_low  = 0
        if market_state:
            weekly_high = market_state.get("weekly_high", 0) or 0
            weekly_low  = market_state.get("weekly_low",  0) or 0

        # Derive weekly trend from dealer inventory + smart money bias
        dealer_inv  = dealer.get("dealer_inventory", "")
        sm_bias     = smart_money.get("smart_money_bias", "NEUTRAL")
        atm_flow    = smart_money.get("atm_flow", "")

        if sm_bias == "BULLISH" or "CALL" in atm_flow:
            trend = "UP"
            structure = "HH_HL"
        elif sm_bias == "BEARISH" or "PUT" in atm_flow:
            trend = "DOWN"
            structure = "LL_LH"
        elif "SHORT_GAMMA" in dealer_inv:
            # Short gamma = trending week expected
            trend = "BREAKOUT"
            structure = "EXPANSION"
        else:
            trend = "SIDEWAYS"
            structure = "RANGE"

        # Weekly swing levels = support/resistance ± wall spread
        wall_spread = compression.get("wall_spread_pct", 0.5) / 100 * spot
        swing_high  = weekly_high or int(liq.get("resistance", spot + wall_spread * 2))
        swing_low   = weekly_low  or int(liq.get("support",    spot - wall_spread * 2))

        return {
            "trend":      trend,
            "structure":  structure,
            "swing_high": swing_high,
            "swing_low":  swing_low,
            "sm_bias":    sm_bias,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # DAILY — PDH/PDL from Java market state, or derived from range
    # ─────────────────────────────────────────────────────────────────────────

    def _daily(self, data: Dict, market_state: Optional[Dict], spot: float) -> Dict:
        volatility  = data.get("volatility_context", {}).get("volatility_engine", {})
        compression = data.get("market_structure", {}).get("compression", {})
        liq         = data.get("liquidity_map", {}).get("support_resistance", {})

        # PDH/PDL from Java market state (most accurate)
        pdh = 0.0
        pdl = 0.0
        if market_state:
            pdh = market_state.get("pdh", 0) or 0
            pdl = market_state.get("pdl", 0) or 0

        # Derive from daily expected move if no Java data
        if not pdh or not pdl:
            daily_move = volatility.get("daily_move", 0) or 0
            pdh = spot + daily_move * 0.4
            pdl = spot - daily_move * 0.4

        # Day structure
        day_range = pdh - pdl if pdh and pdl else 0
        support   = liq.get("support", 0)
        resistance= liq.get("resistance", 0)

        if compression.get("compression_detected"):
            day_structure = "INSIDE_DAY"
        elif day_range and resistance and support:
            range_width = resistance - support
            if day_range < range_width * 0.5:
                day_structure = "INSIDE_DAY"
            elif spot > (pdh + pdl) / 2:
                day_structure = "HH_HL"
            else:
                day_structure = "LL_LH"
        else:
            day_structure = "RANGE"

        # Daily trend
        mid = (pdh + pdl) / 2 if pdh and pdl else spot
        if spot > mid * 1.002:
            trend = "UP"
        elif spot < mid * 0.998:
            trend = "DOWN"
        else:
            trend = "SIDEWAYS"

        return {
            "trend":         trend,
            "pdh":           round(pdh, 2),
            "pdl":           round(pdl, 2),
            "day_structure": day_structure,
            "day_range":     round(day_range, 2),
        }

    # ─────────────────────────────────────────────────────────────────────────
    # INTRADAY — from VWAP bias + pressure + price action
    # ─────────────────────────────────────────────────────────────────────────

    def _intraday(self, data: Dict, spot: float) -> Dict:
        vwap     = data.get("vwap_analysis", {})
        pressure = data.get("market_structure", {}).get("pressure", {})
        delta    = data.get("delta_analysis", {})
        smc      = data.get("smc_analysis", {})

        vwap_bias   = vwap.get("spot_vs_vwap", "AT")
        pressure_v  = pressure.get("pressure", 0) or 0
        delta_bias  = delta.get("delta_bias", "NEUTRAL")
        smc_bias    = smc.get("bias", "NEUTRAL")
        bos         = smc.get("bos", False)
        bos_dir     = smc.get("bos_direction")

        # Intraday structure from SMC BOS
        if bos and bos_dir == "UP":
            structure = "HH_HL"
        elif bos and bos_dir == "DOWN":
            structure = "LL_LH"
        else:
            structure = "RANGE"

        # Intraday trend: VWAP + delta + pressure consensus
        bull_votes = sum([
            vwap_bias == "ABOVE",
            delta_bias == "BULLISH",
            pressure_v > 20,
            smc_bias == "BULLISH",
        ])
        bear_votes = sum([
            vwap_bias == "BELOW",
            delta_bias == "BEARISH",
            pressure_v < -20,
            smc_bias == "BEARISH",
        ])

        if bull_votes >= 3:
            trend = "UP"
        elif bear_votes >= 3:
            trend = "DOWN"
        elif bull_votes == 2 and bear_votes <= 1:
            trend = "UP"
        elif bear_votes == 2 and bull_votes <= 1:
            trend = "DOWN"
        else:
            trend = "SIDEWAYS"

        return {
            "trend":      trend,
            "vwap_bias":  vwap_bias,
            "structure":  structure,
            "delta_bias": delta_bias,
            "pressure":   pressure_v,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # ALIGNMENT — scoring per document table
    # ─────────────────────────────────────────────────────────────────────────

    def _alignment(self, monthly: Dict, weekly: Dict,
                   daily: Dict, intraday: Dict) -> Dict:
        """
        Document scoring:
          Monthly + Weekly + Daily aligned  → +15
          Weekly  + Daily aligned           → +10
          Daily   + Intraday aligned        → +8
          Only intraday                     → +5
          Opposite higher TF                → -10
        """
        mt = monthly.get("trend",  "SIDEWAYS")
        wt = weekly.get("trend",   "SIDEWAYS")
        dt = daily.get("trend",    "SIDEWAYS")
        it = intraday.get("trend", "SIDEWAYS")

        # Simplify BREAKOUT/EXPANSION to directional
        def norm(t):
            if t in ("UP", "BULLISH", "HH_HL"): return "UP"
            if t in ("DOWN", "BEARISH", "LL_LH"): return "DOWN"
            return "NEUTRAL"

        mt_n, wt_n, dt_n, it_n = norm(mt), norm(wt), norm(dt), norm(it)

        m_w = mt_n == wt_n and mt_n != "NEUTRAL"
        w_d = wt_n == dt_n and wt_n != "NEUTRAL"
        d_i = dt_n == it_n and dt_n != "NEUTRAL"
        all_aligned = m_w and w_d and d_i

        # Score
        score = 0
        if all_aligned:
            score = 15
        elif m_w and w_d:
            score = 15
        elif w_d:
            score = 10
        elif d_i:
            score = 8
        elif it_n != "NEUTRAL":
            score = 5

        # Penalty: intraday opposes higher TF
        if it_n != "NEUTRAL" and mt_n != "NEUTRAL" and it_n != mt_n:
            score -= 10

        # Trade bias (document decision rules)
        bias = self._trade_bias(mt_n, wt_n, dt_n, it_n)

        return {
            "monthly_weekly":   m_w,
            "weekly_daily":     w_d,
            "daily_intraday":   d_i,
            "all_aligned":      all_aligned,
            "alignment_score":  score,
            "trade_bias":       bias,
            "direction_filter": it_n if it_n != "NEUTRAL" else dt_n,
            "monthly":          mt_n,
            "weekly":           wt_n,
            "daily":            dt_n,
            "intraday":         it_n,
        }

    def _trade_bias(self, mt: str, wt: str, dt: str, it: str) -> str:
        """
        Document decision table:
          UP+UP+UP        → STRONG_LONG (Buy CE)
          UP+UP+DOWN      → INTRADAY_SHORT (Intraday PE)
          UP+DOWN+DOWN    → SELL_RALLY
          DOWN+DOWN+DOWN  → STRONG_SHORT (Buy PE)
          NEUTRAL×3       → RANGE_GAMMA_TRADE
        """
        if mt == "UP" and wt == "UP" and dt == "UP":
            return "STRONG_LONG"
        if mt == "UP" and wt == "UP" and dt == "DOWN":
            return "INTRADAY_SHORT"
        if mt == "UP" and wt == "DOWN" and dt == "DOWN":
            return "SELL_RALLY"
        if mt == "DOWN" and wt == "DOWN" and dt == "DOWN":
            return "STRONG_SHORT"
        if mt == "DOWN" and wt == "DOWN" and dt == "UP":
            return "INTRADAY_LONG"
        if it == "UP":
            return "INTRADAY_LONG"
        if it == "DOWN":
            return "INTRADAY_SHORT"
        return "RANGE_GAMMA_TRADE"
    