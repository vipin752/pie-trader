from __future__ import annotations


class LiquidityHeatmapEngine:

    """
    Detect liquidity magnets and stop clusters.
    """

    def calculate(self, rows, spot):

        upside = []
        downside = []

        for r in rows:

            strike = r["strike"]

            call_oi = r.get("call_oi", 0)
            put_oi = r.get("put_oi", 0)

            if strike > spot:

                upside.append({
                    "strike": strike,
                    "liquidity": call_oi
                })

            if strike < spot:

                downside.append({
                    "strike": strike,
                    "liquidity": put_oi
                })

        upside = sorted(upside, key=lambda x: x["liquidity"], reverse=True)
        downside = sorted(downside, key=lambda x: x["liquidity"], reverse=True)

        return {

            "upside_magnets": upside[:5],

            "downside_magnets": downside[:5],

            "nearest_upside": upside[0]["strike"] if upside else None,

            "nearest_downside": downside[0]["strike"] if downside else None
        }
        