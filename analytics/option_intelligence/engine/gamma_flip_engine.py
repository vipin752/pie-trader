from __future__ import annotations

from option_intelligence.config.contract_config import get_lot_size


class GammaFlipEngine:

    def calculate(self, rows, spot, symbol):

        lot = get_lot_size(symbol)

        levels = []

        cumulative_gex = 0

        for r in sorted(rows, key=lambda x: x["strike"]):

            strike = r["strike"]

            call_oi = r.get("call_oi", 0)
            put_oi = r.get("put_oi", 0)

            call_gamma = r.get("call_gamma", 0.01)
            put_gamma = r.get("put_gamma", 0.01)

            gex = (call_gamma * call_oi - put_gamma * put_oi) * lot * spot

            cumulative_gex += gex

            levels.append({
                "strike": strike,
                "gex": gex,
                "cumulative_gex": cumulative_gex
            })

        zero_gamma = None

        for i in range(1, len(levels)):

            prev = levels[i - 1]["cumulative_gex"]
            curr = levels[i]["cumulative_gex"]

            if prev < 0 and curr > 0:
                zero_gamma = levels[i]["strike"]
                break

        if not zero_gamma:

            zero_gamma = min(
                levels,
                key=lambda x: abs(x["cumulative_gex"])
            )["strike"]

        if spot > zero_gamma:

            regime = "ABOVE_ZERO_GAMMA"
            behaviour = "MEAN_REVERSION"

        else:

            regime = "BELOW_ZERO_GAMMA"
            behaviour = "VOLATILITY_EXPANSION"

        return {

            "zero_gamma": zero_gamma,

            "regime": regime,

            "expected_behavior": behaviour,

            "distance_from_flip": round(spot - zero_gamma, 2)
        }
        