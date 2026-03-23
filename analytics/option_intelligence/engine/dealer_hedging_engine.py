from __future__ import annotations

from option_intelligence.config.contract_config import get_lot_size


class DealerHedgingEngine:

    def calculate(self, rows, spot, symbol):

        lot = get_lot_size(symbol)

        total_dex = 0.0
        total_gex = 0.0

        levels = []

        for r in rows:

            strike = r["strike"]

            call_oi = r.get("call_oi", 0)
            put_oi = r.get("put_oi", 0)

            call_gamma = r.get("call_gamma", 0.01)
            put_gamma = r.get("put_gamma", 0.01)

            # simple delta approximation
            call_delta = 0.5 if strike <= spot else 0.25
            put_delta = -0.5 if strike >= spot else -0.25

            call_dex = call_delta * call_oi * lot
            put_dex = put_delta * put_oi * lot

            dex = call_dex + put_dex

            gex = (call_gamma * call_oi - put_gamma * put_oi) * lot * spot

            total_dex += dex
            total_gex += gex

            levels.append({
                "strike": strike,
                "dex": round(dex, 2),
                "gex": round(gex, 2)
            })

        # dealer regime

        if total_gex > 0:
            regime = "POSITIVE_GAMMA"
        else:
            regime = "NEGATIVE_GAMMA"

        # hedge direction

        if total_dex > 0:
            hedge_direction = "DEALERS_LONG_HEDGE"
        else:
            hedge_direction = "DEALERS_SHORT_HEDGE"

        # volatility expectation

        if regime == "POSITIVE_GAMMA":
            vol_state = "LOW_VOL_MEAN_REVERSION"
        else:
            vol_state = "HIGH_VOL_TREND"

        return {

            "dealer_regime": regime,

            "hedge_direction": hedge_direction,

            "volatility_state": vol_state,

            "total_dex": round(total_dex, 2),

            "total_gex": round(total_gex, 2),

            "levels": sorted(
                levels,
                key=lambda x: abs(x["gex"]),
                reverse=True
            )[:10]
        }
        