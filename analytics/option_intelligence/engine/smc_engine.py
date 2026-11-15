"""
PIE TRADER — option_intelligence/engine/smc_engine.py  v2.0

Full SMC / ICT Engine.

Document architecture (from final review):
  SMC/ICT = PRICE ACTION CORE  (entry model)
  Options  = CONTEXT            (confirmation)

Implements all 8 ICT concepts:
  (A) Market Structure  — HH, HL, LH, LL, BOS, CHoCH, MSS
  (B) Liquidity         — Buy-side, Sell-side, Equal H/L, Sweep, Inducement
  (C) Order Block       — Bullish OB, Bearish OB, Mitigation, Breaker
  (D) Fair Value Gap    — FVG, IFVG, nearest, filled
  (E) PD Array          — Premium / Discount / Equilibrium
  (F) OTE Zone          — 62–79% retracement
  (G) AMD Model         — Accumulation / Manipulation / Distribution
  (H) Killzone          — India Open / NY Open / Power Hour / Lunch Avoid

Backward compatible: all v1 flat keys preserved so TrapEngine /
BreakoutTriggerEngine keep working without changes.
"""
from __future__ import annotations

from datetime import datetime, time as dt_time
from typing import Dict, Any, List, Optional, Tuple

try:
    import pytz
    IST = pytz.timezone("Asia/Kolkata")
except ImportError:
    import datetime as _dt
    IST = _dt.timezone(_dt.timedelta(hours=5, minutes=30))


class SMCEngine:

    # ─────────────────────────────────────────────────────────────────────────
    # PUBLIC
    # ─────────────────────────────────────────────────────────────────────────

    def analyze(self,
                spot: float,
                rows: List[Dict],
                liquidity: Dict,
                prev_spot: float = 0.0) -> Dict[str, Any]:
        """
        Full SMC/ICT analysis.
        Args:
            spot:      Current spot price
            rows:      Option chain strike rows
            liquidity: {"support": int, "resistance": int}
            prev_spot: Previous cycle spot for structure detection
        """
        try:
            support    = float(liquidity.get("support",    spot - 100) or (spot - 100))
            resistance = float(liquidity.get("resistance", spot + 100) or (spot + 100))
            prev_spot  = prev_spot if prev_spot > 0 else spot

            ms    = self._market_structure(spot, prev_spot, support, resistance, rows)
            liq   = self._liquidity(spot, prev_spot, support, resistance, rows)
            ob    = self._order_blocks(rows, spot)
            fvg   = self._fvg(rows, spot)
            pd    = self._pd_array(support, resistance, spot)
            ote   = self._ote(support, resistance, spot)
            amd   = self._amd(spot, prev_spot, support, resistance, liq)
            tm    = self._killzone()

            bias, signal, setup, setup_score = self._composite(
                ms, liq, ob, fvg, pd, ote, amd, tm
            )

            return {
                # ── Structured ICT output ─────────────────────────────────────
                "smc": {
                    "market_structure": ms,
                    "liquidity":        liq,
                    "order_block":      ob,
                    "fvg":              fvg,
                    "pd_array":         pd,
                    "ote":              ote,
                    "amd":              amd,
                    "time_model":       tm,
                },
                "bias":        bias,
                "signal":      signal,
                "setup":       setup,
                "setup_score": setup_score,

                # ── Backward-compat flat keys (v1 consumers) ──────────────────
                "liquidity_sweep":  liq.get("sweep", "NONE"),
                "order_block":      (ob.get("bearish_ob") or {}).get("zone_high") or
                                    (ob.get("bullish_ob") or {}).get("zone_low"),
                "order_block_type": "RESISTANCE" if ms.get("trend") == "BEARISH" else "SUPPORT",
                "fvg":              (fvg.get("bullish_fvg") or [None])[0],
                "fvg_type":         fvg.get("nearest_fvg"),
                "bos":              ms.get("bos", False),
                "bos_direction":    "DOWN" if ms.get("trend") == "BEARISH" else "UP",
                "choch":            ms.get("choch", False),
                "key_levels":       [int(support), int(resistance)],
            }

        except Exception as e:
            return {
                "smc": {}, "bias": "NEUTRAL", "signal": "NEUTRAL",
                "setup": "NONE", "setup_score": 0,
                "liquidity_sweep": "NONE", "order_block": None,
                "order_block_type": None, "fvg": None, "fvg_type": None,
                "bos": False, "bos_direction": None, "choch": False,
                "key_levels": [], "error": str(e),
            }

    # ─────────────────────────────────────────────────────────────────────────
    # (A) MARKET STRUCTURE — BOS, CHoCH, MSS
    # ─────────────────────────────────────────────────────────────────────────

    def _market_structure(self, spot, prev_spot, support, resistance, rows) -> Dict:
        # OI distribution → structural bias
        put_oi_below = sum(int(r.get("put_oi", 0) or 0)
                           for r in rows
                           if float(r.get("strike", 0) or 0) <= spot)
        call_oi_above = sum(int(r.get("call_oi", 0) or 0)
                            for r in rows
                            if float(r.get("strike", 0) or 0) >= spot)

        if call_oi_above > put_oi_below * 1.2:
            trend = "BEARISH"   # heavy call OI = ceiling = bearish bias
        elif put_oi_below > call_oi_above * 1.2:
            trend = "BULLISH"   # heavy put OI = floor = bullish bias
        else:
            trend = "SIDEWAYS"

        bos   = spot > resistance or spot < support
        choch = (prev_spot > support and spot < support) or \
                (prev_spot < resistance and spot > resistance)
        mss   = abs(spot - prev_spot) > (resistance - support) * 0.3

        structure = "HH_HL" if trend == "BULLISH" else \
                    "LL_LH" if trend == "BEARISH" else "RANGE"

        return {
            "trend":     trend,
            "bos":       bos,
            "choch":     choch,
            "mss":       mss,
            "structure": structure,
            "last_hh":   int(resistance),
            "last_hl":   int(support),
            "last_lh":   int(resistance),
            "last_ll":   int(support),
        }

    # ─────────────────────────────────────────────────────────────────────────
    # (B) LIQUIDITY — Buy/Sell side, Equal H/L, Sweep, Inducement
    # ─────────────────────────────────────────────────────────────────────────

    def _liquidity(self, spot, prev_spot, support, resistance, rows) -> Dict:
        buy_side  = int(resistance)   # buy stops resting above highs
        sell_side = int(support)      # sell stops resting below lows

        # Equal highs/lows = OI clusters at same level
        equal_highs = self._dominant_oi_level(rows, spot, above=True,  oi_key="call_oi")
        equal_lows  = self._dominant_oi_level(rows, spot, above=False, oi_key="put_oi")

        # Sweep: price pierced a level then recovered (stop hunt)
        buffer = max(15, (resistance - support) * 0.02)
        if prev_spot >= support and spot < support and spot > (support - buffer * 2):
            sweep = "SELL_SIDE_SWEEP"       # swept below lows → bullish reversal
        elif prev_spot <= resistance and spot > resistance and spot < (resistance + buffer * 2):
            sweep = "BUY_SIDE_SWEEP"        # swept above highs → bearish reversal
        elif spot < (support - buffer):
            sweep = "BELOW_SUPPORT"
        elif spot > (resistance + buffer):
            sweep = "ABOVE_RESISTANCE"
        else:
            sweep = "NONE"

        # Inducement: price within 0.3% of a key level → trap setup forming
        dist_sell = abs(spot - sell_side) / spot * 100 if spot > 0 else 100
        dist_buy  = abs(spot - buy_side)  / spot * 100 if spot > 0 else 100
        inducement = dist_sell < 0.3 or dist_buy < 0.3

        return {
            "buy_side":    buy_side,
            "sell_side":   sell_side,
            "equal_highs": equal_highs,
            "equal_lows":  equal_lows,
            "sweep":       sweep,
            "inducement":  inducement,
        }

    def _dominant_oi_level(self, rows, spot, above: bool, oi_key: str) -> Optional[int]:
        candidates = [r for r in rows
                      if (float(r.get("strike", 0) or 0) > spot) == above
                      and abs(float(r.get("strike", 0) or 0) - spot) < 500]
        if not candidates:
            return None
        best = max(candidates, key=lambda r: int(r.get(oi_key, 0) or 0))
        return int(float(best.get("strike", 0)))

    # ─────────────────────────────────────────────────────────────────────────
    # (C) ORDER BLOCKS — Bullish OB / Bearish OB / Mitigation
    # ─────────────────────────────────────────────────────────────────────────

    def _order_blocks(self, rows, spot) -> Dict:
        """
        ICT: Bullish OB = last down candle before impulsive up move.
        Proxy: highest put OI concentration below spot = demand zone (bullish OB).
               highest call OI concentration above spot = supply zone (bearish OB).
        Mitigation = price returned into OB zone.
        Breaker    = price broke fully through OB (OB flipped).
        """
        best_bull_oi = best_bear_oi = 0
        bull_strike  = bear_strike  = None

        for r in rows:
            s       = int(float(r.get("strike", 0) or 0))
            call_oi = int(r.get("call_oi", 0) or 0)
            put_oi  = int(r.get("put_oi",  0) or 0)
            if abs(s - spot) > 600:
                continue
            if s < spot and put_oi > best_bull_oi:
                best_bull_oi = put_oi
                bull_strike  = s
            if s > spot and call_oi > best_bear_oi:
                best_bear_oi = call_oi
                bear_strike  = s

        gap = 30    # OB zone width proxy (candle body width)

        bullish_ob = None
        if bull_strike:
            mitigated = spot <= (bull_strike + gap)         # price came down into OB
            broken    = spot < (bull_strike - 10)           # OB fully broken
            bullish_ob = {
                "zone_low":  bull_strike,
                "zone_high": bull_strike + gap,
                "mitigated": mitigated,
                "broken":    broken,
                "type":      "BREAKER" if broken else "MITIGATION" if mitigated else "FRESH",
            }

        bearish_ob = None
        if bear_strike:
            mitigated = spot >= (bear_strike - gap)         # price came up into OB
            broken    = spot > (bear_strike + 10)           # OB fully broken
            bearish_ob = {
                "zone_low":  bear_strike - gap,
                "zone_high": bear_strike,
                "mitigated": mitigated,
                "broken":    broken,
                "type":      "BREAKER" if broken else "MITIGATION" if mitigated else "FRESH",
            }

        return {"bullish_ob": bullish_ob, "bearish_ob": bearish_ob}

    # ─────────────────────────────────────────────────────────────────────────
    # (D) FAIR VALUE GAP — FVG, IFVG, nearest, filled
    # ─────────────────────────────────────────────────────────────────────────

    def _fvg(self, rows, spot) -> Dict:
        """
        ICT FVG: gap in price where no candle overlap → imbalance zone.
        Proxy: gap in consecutive call/put LTP across 3 strikes.
        Price tends to return to fill FVG zones.
        """
        sorted_rows = sorted(rows, key=lambda r: float(r.get("strike", 0) or 0))
        bullish_fvg = bearish_fvg = None
        best_bull = best_bear = 0

        for i in range(1, len(sorted_rows) - 1):
            prev_r = sorted_rows[i - 1]
            curr_r = sorted_rows[i]
            next_r = sorted_rows[i + 1]
            curr_s = float(curr_r.get("strike", 0) or 0)
            if abs(curr_s - spot) > 500:
                continue
            c_prev = float(prev_r.get("call_ltp", 0) or 0)
            c_next = float(next_r.get("call_ltp", 0) or 0)
            p_prev = float(prev_r.get("put_ltp",  0) or 0)
            p_next = float(next_r.get("put_ltp",  0) or 0)

            # Bullish FVG: upward imbalance in call premium below spot
            bull_gap = c_next - c_prev
            if bull_gap > best_bull and bull_gap > 5 and curr_s < spot:
                best_bull   = bull_gap
                s           = int(curr_s)
                bullish_fvg = [s, s + 20]

            # Bearish FVG: downward imbalance in put premium above spot
            bear_gap = p_prev - p_next
            if bear_gap > best_bear and bear_gap > 5 and curr_s > spot:
                best_bear   = bear_gap
                s           = int(curr_s)
                bearish_fvg = [s, s + 20]

        nearest = None
        if bullish_fvg and bearish_fvg:
            nearest = "BULLISH" if abs(spot - bullish_fvg[0]) < abs(spot - bearish_fvg[0]) \
                      else "BEARISH"
        elif bullish_fvg:
            nearest = "BULLISH"
        elif bearish_fvg:
            nearest = "BEARISH"

        bull_filled = bullish_fvg and spot > bullish_fvg[1]
        bear_filled = bearish_fvg and spot < bearish_fvg[0]

        return {
            "bullish_fvg": bullish_fvg,
            "bearish_fvg": bearish_fvg,
            "nearest_fvg": nearest,
            "filled":      bool(bull_filled or bear_filled),
        }

    # ─────────────────────────────────────────────────────────────────────────
    # (E) PD ARRAY — Premium / Discount / Equilibrium
    # ─────────────────────────────────────────────────────────────────────────

    def _pd_array(self, support, resistance, spot) -> Dict:
        """
        ICT PD Array:
          Premium  (>62%) = sell zone
          Discount (<38%) = buy zone
          Equilibrium     = 50%
        """
        if not support or not resistance or resistance <= support:
            return {}
        total    = resistance - support
        equil    = support + total * 0.50
        premium  = support + total * 0.62
        discount = support + total * 0.38
        zone = ("PREMIUM"      if spot > premium  else
                "DISCOUNT"     if spot < discount  else
                "EQUILIBRIUM")
        return {
            "range_high":   int(resistance),
            "range_low":    int(support),
            "premium":      round(premium, 0),
            "discount":     round(discount, 0),
            "equilibrium":  round(equil, 0),
            "current_zone": zone,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # (F) OTE ZONE — Optimal Trade Entry (62–79% retracement)
    # ─────────────────────────────────────────────────────────────────────────

    def _ote(self, support, resistance, spot) -> Dict:
        """
        ICT OTE: best entry is 62–79% retracement of the impulsive leg.
        For bearish: from resistance high, OTE is 62–79% pullback down.
        """
        if not support or not resistance or resistance <= support:
            return {"zone": None, "active": False}
        total  = resistance - support
        ote_lo = resistance - total * 0.79
        ote_hi = resistance - total * 0.62
        active = ote_lo <= spot <= ote_hi
        return {
            "zone":   [round(ote_lo, 0), round(ote_hi, 0)],
            "active": active,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # (G) AMD MODEL — Accumulation / Manipulation / Distribution
    # ─────────────────────────────────────────────────────────────────────────

    def _amd(self, spot, prev_spot, support, resistance, liq) -> Dict:
        sweep = liq.get("sweep", "NONE")
        rng   = max(resistance - support, 1)
        pos   = (spot - support) / rng

        if sweep != "NONE":
            phase = "MANIPULATION"          # stop hunt phase
        elif 0.35 < pos < 0.65 and abs(spot - prev_spot) < rng * 0.1:
            phase = "ACCUMULATION"          # tight range, building energy
        else:
            phase = "DISTRIBUTION"          # trending / expanding

        return {"phase": phase}

    # ─────────────────────────────────────────────────────────────────────────
    # (H) KILLZONE — ICT Time Model
    # ─────────────────────────────────────────────────────────────────────────

    def _killzone(self) -> Dict:
        try:
            now = datetime.now(IST).time()
        except Exception:
            from datetime import datetime as _dt
            now = _dt.now().time()

        if   dt_time(9,  15) <= now < dt_time(10, 0):
            kz, bhvr = "INDIA_OPEN",   "MANIPULATION"
        elif dt_time(13, 30) <= now < dt_time(15,  0):
            kz, bhvr = "NY_OPEN",      "EXPANSION"
        elif dt_time(14, 30) <= now < dt_time(15, 15):
            kz, bhvr = "POWER_HOUR",   "TREND"
        elif dt_time(12, 30) <= now < dt_time(13, 30):
            kz, bhvr = "LUNCH_AVOID",  "AVOID"
        else:
            kz, bhvr = "NORMAL",       "STANDARD"

        return {
            "killzone":          kz,
            "expected_behavior": bhvr,
            "avoid_trade":       kz == "LUNCH_AVOID",
        }

    # ─────────────────────────────────────────────────────────────────────────
    # COMPOSITE BIAS — Document: "Liquidity Sweep + OB + FVG" = highest prob
    # ─────────────────────────────────────────────────────────────────────────

    def _composite(self, ms, liq, ob, fvg, pd, ote,
                   amd, tm) -> Tuple[str, str, str, int]:
        bull = bear = 0
        setups: List[str] = []

        # Market structure (BOS/CHoCH)
        if ms.get("trend") == "BULLISH":  bull += 2
        if ms.get("trend") == "BEARISH":  bear += 2
        if ms.get("bos"):
            if ms.get("trend") == "BEARISH": bear += 2; setups.append("BOS")
            else:                             bull += 2; setups.append("BOS")
        if ms.get("choch"):               bear += 2; setups.append("CHoCH")

        # Liquidity sweep (highest weight)
        sweep = liq.get("sweep", "NONE")
        if sweep == "SELL_SIDE_SWEEP":    bull += 3; setups.append("LIQUIDITY_SWEEP")
        if sweep in ("BUY_SIDE_SWEEP", "ABOVE_RESISTANCE"):
                                          bear += 3; setups.append("LIQUIDITY_SWEEP")

        # Order blocks
        bob = ob.get("bullish_ob") or {}
        beb = ob.get("bearish_ob") or {}
        if bob.get("mitigated") and not bob.get("broken"): bull += 3; setups.append("BULLISH_OB")
        if beb.get("mitigated") and not beb.get("broken"): bear += 3; setups.append("BEARISH_OB")

        # FVG
        nf = fvg.get("nearest_fvg")
        if nf == "BULLISH" and not fvg.get("filled"): bull += 2; setups.append("FVG")
        if nf == "BEARISH" and not fvg.get("filled"): bear += 2; setups.append("FVG")

        # PD array
        if pd.get("current_zone") == "DISCOUNT":  bull += 1
        if pd.get("current_zone") == "PREMIUM":   bear += 1

        # OTE
        if ote.get("active"):             bear += 2; setups.append("OTE")

        # AMD
        if amd.get("phase") == "MANIPULATION": bull += 1; bear += 1

        # Killzone
        if tm.get("expected_behavior") == "EXPANSION": bull += 1; bear += 1
        if tm.get("avoid_trade"):         bull -= 3;  bear -= 3

        if   bull > bear  and bull  >= 4: bias, signal = "BULLISH", "BUY"
        elif bear > bull  and bear  >= 4: bias, signal = "BEARISH", "SELL"
        else:                             bias, signal = "NEUTRAL", "NEUTRAL"

        setup_name  = " + ".join(setups) if setups else "NONE"
        setup_score = max(bull, bear) * 2

        return bias, signal, setup_name, setup_score
    