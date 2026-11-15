from __future__ import annotations


class SmartMoneyFlowEngine:

    """
    Detects institutional options positioning.

    Uses:
    - Volume spikes
    - OI build-up
    - ATM concentration
    """

    def detect(self, strikes, spot):

        flows = []

        total_call_volume = 0
        total_put_volume = 0

        total_call_oi = 0
        total_put_oi = 0

        atm_band = []

        for row in strikes:

            strike = row["strike"]

            call_vol = row.get("call_volume", 0)
            put_vol = row.get("put_volume", 0)

            call_oi = row.get("call_oi", 0)
            put_oi = row.get("put_oi", 0)

            total_call_volume += call_vol
            total_put_volume += put_vol

            total_call_oi += call_oi
            total_put_oi += put_oi

            if abs(strike - spot) <= 100:

                atm_band.append(row)

            # detect unusual activity

            if call_vol > 3 * call_oi and call_vol > 500000:

                flows.append({

                    "strike": strike,

                    "type": "CALL_SWEEP",

                    "volume": call_vol

                })

            if put_vol > 3 * put_oi and put_vol > 500000:

                flows.append({

                    "strike": strike,

                    "type": "PUT_SWEEP",

                    "volume": put_vol

                })

        # market bias

        bias = "NEUTRAL"

        if total_call_volume > total_put_volume * 1.3:

            bias = "BULLISH_POSITIONING"

        elif total_put_volume > total_call_volume * 1.3:

            bias = "BEARISH_POSITIONING"

        # ATM pressure

        atm_call = sum(x.get("call_volume", 0) for x in atm_band)
        atm_put = sum(x.get("put_volume", 0) for x in atm_band)

        atm_flow = "BALANCED"

        if atm_call > atm_put * 1.2:

            atm_flow = "ATM_CALL_BUYING"

        elif atm_put > atm_call * 1.2:

            atm_flow = "ATM_PUT_BUYING"

        return {

            "smart_money_bias": bias,

            "atm_flow": atm_flow,

            "unusual_flows": flows[:10]

        }
        