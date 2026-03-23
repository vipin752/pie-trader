"""
PIE TRADER — option_intelligence/engine/vwap_engine.py

Determines intraday VWAP bias from option chain data.

Since Python doesn't receive raw tick OHLCV data (only option chain),
VWAP is approximated using:
  1. Weighted average of ATM LTP using option volume as weights
  2. Compared against spot to determine bias

For accurate VWAP: Java MarketStateKafkaProducer should include
vwap in MarketStateDTO (already defined as a field — just needs
Java to compute and send it). When available, uses Java-provided VWAP.

Output:
  {
    "vwap":           float,    # computed or Java-provided
    "spot_vs_vwap":   "ABOVE" | "BELOW" | "AT",
    "vwap_bias":      "BULLISH" | "BEARISH" | "NEUTRAL",
    "deviation_pct":  float,    # how far spot is from VWAP (%)
    "signal":         "BUY" | "SELL" | "NEUTRAL",
    "source":         "JAVA_MARKET_STATE" | "OPTION_CHAIN_APPROX"
  }
"""

from typing import Dict, Any, List


# Deviation threshold — if spot is within 0.2% of VWAP, treat as AT
AT_VWAP_THRESHOLD_PCT = 0.20


class VWAPEngine:

    def calculate(self,
                  spot: float,
                  rows: List[Dict],
                  market_state: Dict = None) -> Dict[str, Any]:
        """
        Args:
            spot:         Current index spot price
            rows:         Strike rows from option chain
            market_state: Optional MarketState from Java (pie.market.state)
                          If present and contains vwap, uses it directly.
        """
        try:
            vwap   = 0.0
            source = "OPTION_CHAIN_APPROX"

            # Priority 1: Use Java-provided VWAP if available
            if market_state and market_state.get("vwap", 0) > 0:
                vwap   = float(market_state["vwap"])
                source = "JAVA_MARKET_STATE"
            else:
                # Compute VWAP proxy from option chain
                vwap = self._compute_from_chain(spot, rows)
                source = "OPTION_CHAIN_APPROX"

            if vwap <= 0:
                vwap = spot  # fallback

            # Compare spot vs VWAP
            deviation_pct = ((spot - vwap) / vwap * 100) if vwap > 0 else 0.0

            if abs(deviation_pct) <= AT_VWAP_THRESHOLD_PCT:
                position = "AT"
                bias     = "NEUTRAL"
                signal   = "NEUTRAL"
            elif deviation_pct > 0:
                position = "ABOVE"
                bias     = "BULLISH"
                signal   = "BUY"
            else:
                position = "BELOW"
                bias     = "BEARISH"
                signal   = "SELL"

            return {
                "vwap":          round(vwap, 2),
                "spot_vs_vwap":  position,
                "vwap_bias":     bias,
                "deviation_pct": round(deviation_pct, 3),
                "signal":        signal,
                "source":        source,
            }

        except Exception as e:
            return {
                "vwap": spot, "spot_vs_vwap": "AT",
                "vwap_bias": "NEUTRAL", "deviation_pct": 0.0,
                "signal": "NEUTRAL", "source": "ERROR",
                "error": str(e)
            }

    def _compute_from_chain(self, spot: float, rows: List[Dict]) -> float:
        """
        VWAP proxy from option chain:
        Weighted average of (call_ltp + put_ltp) / 2 using (call_volume + put_volume) as weight.
        Only ATM ± 5 strikes used for accuracy.
        """
        if not rows or spot <= 0:
            return spot

        total_weighted = 0.0
        total_volume   = 0.0
        atm_approx     = round(spot / 50) * 50  # nearest 50

        for row in rows:
            strike = float(row.get("strike", 0) or 0)
            if abs(strike - atm_approx) > 250:  # only ±5 strikes
                continue

            call_ltp = float(row.get("call_ltp", 0) or 0)
            put_ltp  = float(row.get("put_ltp",  0) or 0)
            call_vol = float(row.get("call_volume", 0) or 0)
            put_vol  = float(row.get("put_volume",  0) or 0)

            mid_price = (call_ltp + put_ltp) / 2
            volume    = call_vol + put_vol

            if mid_price > 0 and volume > 0:
                # Map option mid price back to underlying equivalent
                # Using put-call parity approximation: spot ≈ strike + call - put
                synthetic_spot = strike + call_ltp - put_ltp
                total_weighted += synthetic_spot * volume
                total_volume   += volume

        return total_weighted / total_volume if total_volume > 0 else spot
    