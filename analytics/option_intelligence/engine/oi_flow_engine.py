from __future__ import annotations


class OIFlowEngine:

    def __init__(self):
        self.previous = {}

    def detect(self, strikes, previous_rows=None):

        flows = []

        prev_map = {}

        if previous_rows:
            prev_map = {str(s["strike"]): s for s in previous_rows}

        for s in strikes:

            prev = prev_map.get(str(s["strike"]))

            if not prev:
                continue

            call_prev = prev.get("call_oi", 0)
            put_prev = prev.get("put_oi", 0)

            call_now = s.get("call_oi", 0)
            put_now = s.get("put_oi", 0)

            c_chg = call_now - call_prev
            p_chg = put_now - put_prev

            strike = s["strike"]

            signal = None

            if c_chg > 0:
                signal = "CALL_OI_BUILD"
            elif c_chg < 0:
                signal = "CALL_OI_UNWIND"
            elif p_chg > 0:
                signal = "PUT_OI_BUILD"
            elif p_chg < 0:
                signal = "PUT_OI_UNWIND"

            if signal:

                flows.append({
                    "strike": strike,
                    "signal": signal,
                    "call_change": c_chg,
                    "put_change": p_chg
                })

        return {
            "oi_flows": flows[:10]
        }
        