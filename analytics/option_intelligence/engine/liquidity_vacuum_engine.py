from __future__ import annotations


class LiquidityVacuumEngine:

    """
    Detects zones where option open interest is extremely low.
    These areas create fast market moves.
    """

    def detect(self, strikes, spot):

        zones = []

        threshold = 5000

        sorted_strikes = sorted(strikes, key=lambda x: x["strike"])

        for i in range(len(sorted_strikes) - 1):

            s1 = sorted_strikes[i]
            s2 = sorted_strikes[i + 1]

            oi1 = s1.get("call_oi", 0) + s1.get("put_oi", 0)
            oi2 = s2.get("call_oi", 0) + s2.get("put_oi", 0)

            if oi1 < threshold and oi2 < threshold:

                zones.append({

                    "from_strike": s1["strike"],
                    "to_strike": s2["strike"]

                })

        # detect if price is inside vacuum

        active = None

        for z in zones:

            if z["from_strike"] <= spot <= z["to_strike"]:

                active = z

                break

        return {

            "vacuum_zones": zones,

            "active_vacuum": active

        }
        