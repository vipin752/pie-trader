from __future__ import annotations

from option_intelligence.config.contract_config import get_lot_size


class GammaProfileEngine:

    """
    Build gamma exposure curve across strikes.
    Used to visualize dealer positioning.
    """

    def build(self, rows, spot, symbol):

        lot = get_lot_size(symbol)

        curve = []

        total_gex = 0

        for r in rows:

            strike = r["strike"]

            call_oi = r.get("call_oi", 0)
            put_oi = r.get("put_oi", 0)

            call_gamma = r.get("call_gamma", 0.01)
            put_gamma = r.get("put_gamma", 0.01)

            gex = (call_gamma * call_oi - put_gamma * put_oi) * lot * spot

            total_gex += gex

            curve.append({

                "strike": strike,

                "gex": round(gex,2),

                "cumulative_gex": round(total_gex,2)

            })

        # strongest gamma levels

        key_levels = sorted(
            curve,
            key=lambda x: abs(x["gex"]),
            reverse=True
        )[:10]

        # detect gamma walls

        resistance = max(curve, key=lambda x: x["gex"])["strike"]

        support = min(curve, key=lambda x: x["gex"])["strike"]

        return {

            "gamma_curve": curve,

            "strongest_gamma_levels": key_levels,

            "gamma_resistance": resistance,

            "gamma_support": support
        }
        