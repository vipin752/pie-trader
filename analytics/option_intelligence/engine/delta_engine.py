"""
PIE TRADER — option_intelligence/engine/delta_engine.py

Determines real buying vs selling from OI change + price action.
This is one of the most important confirmation signals for direction.

Delta Logic (from document):
  Price ↑ + Call OI ↑ = Call Writing         → BEARISH (resistance building)
  Price ↑ + Call OI ↓ = Short Covering        → BULLISH (squeeze up)
  Price ↓ + Put OI ↑  = Put Buying            → BEARISH (breakdown expected)
  Price ↓ + Put OI ↓  = Long Unwinding        → NEUTRAL (weak)

Output:
  {
    "delta_bias":   "BEARISH" | "BULLISH" | "NEUTRAL",
    "call_delta":   float,   # net call OI change weighted by direction
    "put_delta":    float,   # net put OI change weighted by direction
    "net_delta":    float,   # call_delta - put_delta
    "activity":     "PUT_BUYING" | "CALL_WRITING" | "SHORT_COVERING" | "LONG_UNWINDING" | "NEUTRAL",
    "signal":       "SELL" | "BUY" | "NEUTRAL",
    "confidence":   "HIGH" | "MEDIUM" | "LOW"
  }
"""

from typing import Dict, Any, List


class DeltaEngine:

    def calculate(self, rows: List[Dict], price_action: Dict) -> Dict[str, Any]:
        """
        Args:
            rows:         list of strike dicts with call_oi, put_oi,
                          call_chg_oi, put_chg_oi, call_ltp, put_ltp
            price_action: {"current": float, "previous": float, "change": float}
        """
        try:
            price_change = price_action.get("change", 0)

            total_call_oi_chg = 0.0
            total_put_oi_chg  = 0.0
            total_call_oi     = 0.0
            total_put_oi      = 0.0

            for row in rows:
                call_oi     = float(row.get("call_oi", 0) or 0)
                put_oi      = float(row.get("put_oi", 0) or 0)
                call_chg_oi = float(row.get("call_chg_oi", 0) or 0)
                put_chg_oi  = float(row.get("put_chg_oi", 0) or 0)

                total_call_oi     += call_oi
                total_put_oi      += put_oi
                total_call_oi_chg += call_chg_oi
                total_put_oi_chg  += put_chg_oi

            # Classify activity based on price direction + OI change
            activity, delta_bias, signal = self._classify(
                price_change, total_call_oi_chg, total_put_oi_chg
            )

            # Net delta: positive = call heavy (bearish), negative = put heavy (bearish via puts)
            call_delta = total_call_oi_chg
            put_delta  = total_put_oi_chg
            net_delta  = call_delta - put_delta

            # Confidence based on magnitude of OI change
            total_oi   = total_call_oi + total_put_oi
            chg_ratio  = abs(total_call_oi_chg + total_put_oi_chg) / total_oi if total_oi > 0 else 0
            confidence = "HIGH" if chg_ratio > 0.05 else "MEDIUM" if chg_ratio > 0.02 else "LOW"

            return {
                "delta_bias":  delta_bias,
                "call_delta":  round(call_delta, 0),
                "put_delta":   round(put_delta, 0),
                "net_delta":   round(net_delta, 0),
                "activity":    activity,
                "signal":      signal,
                "confidence":  confidence,
                "price_change": round(price_change, 2),
            }

        except Exception as e:
            return {
                "delta_bias": "NEUTRAL",
                "call_delta": 0, "put_delta": 0, "net_delta": 0,
                "activity": "NEUTRAL", "signal": "NEUTRAL",
                "confidence": "LOW", "error": str(e)
            }

    def _classify(self, price_change: float,
                  call_oi_chg: float, put_oi_chg: float):
        """
        Returns (activity, delta_bias, signal)
        """
        price_up   = price_change > 0
        price_down = price_change < 0
        call_build = call_oi_chg > 0
        put_build  = put_oi_chg  > 0

        # Price UP + Call OI building → Writers selling calls = BEARISH
        if price_up and call_build and not put_build:
            return "CALL_WRITING", "BEARISH", "SELL"

        # Price UP + Call OI falling → Short covering = BULLISH
        if price_up and not call_build:
            return "SHORT_COVERING", "BULLISH", "BUY"

        # Price DOWN + Put OI building → Put buyers expect more down = BEARISH
        if price_down and put_build and not call_build:
            return "PUT_BUYING", "BEARISH", "SELL"

        # Price DOWN + Put OI falling → Longs unwinding = NEUTRAL/WEAK
        if price_down and not put_build:
            return "LONG_UNWINDING", "NEUTRAL", "NEUTRAL"

        # Both OI building = writing on both sides = RANGE
        if call_build and put_build:
            return "STRADDLE_WRITING", "NEUTRAL", "NEUTRAL"

        return "NEUTRAL", "NEUTRAL", "NEUTRAL"
    