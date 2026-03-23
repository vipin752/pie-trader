from __future__ import annotations


class PCREngine:

    def __init__(self):
        pass


    def calculate(self, strikes):

        if not strikes:
            return {
                "pcr": 0,
                "put_oi": 0,
                "call_oi": 0,
                "sentiment": "neutral"
            }

        call_oi_total = 0
        put_oi_total = 0

        for row in strikes:

            call_oi_total += row.get("call_oi", 0)
            put_oi_total += row.get("put_oi", 0)

        if call_oi_total == 0:
            pcr = 0
        else:
            pcr = put_oi_total / call_oi_total

        # sentiment classification
        if pcr > 1.2:
            sentiment = "bullish"
        elif pcr < 0.8:
            sentiment = "bearish"
        else:
            sentiment = "neutral"

        return {
            "pcr": round(pcr, 3),
            "put_oi": put_oi_total,
            "call_oi": call_oi_total,
            "sentiment": sentiment
        }
        