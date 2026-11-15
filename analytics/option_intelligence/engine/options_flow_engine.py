from __future__ import annotations

class OptionsFlowEngine:

    """
    Detect unusual options activity and smart money flows.
    """

    def detect(self, rows):

        flows = []

        for r in rows:

            strike = r["strike"]

            call_oi = r.get("call_oi", 0)
            put_oi = r.get("put_oi", 0)

            call_vol = r.get("call_volume", 0)
            put_vol = r.get("put_volume", 0)

            call_ltp = r.get("call_ltp", 0)
            put_ltp = r.get("put_ltp", 0)

            if call_vol > call_oi * 2:

                flows.append({
                    "strike": strike,
                    "type": "CALL_SWEEP",
                    "volume": call_vol,
                    "price": call_ltp
                })

            if put_vol > put_oi * 2:

                flows.append({
                    "strike": strike,
                    "type": "PUT_SWEEP",
                    "volume": put_vol,
                    "price": put_ltp
                })

        flows = sorted(flows, key=lambda x: x["volume"], reverse=True)

        return {
            "smart_money_flows": flows[:10]
        }
        