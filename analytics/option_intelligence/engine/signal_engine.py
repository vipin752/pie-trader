class SignalEngine:

    def analyze(self, data):

        call_oi = sum([x["call_oi"] for x in data])

        put_oi = sum([x["put_oi"] for x in data])

        if put_oi > call_oi:

            bias = "BULLISH"

        elif call_oi > put_oi:

            bias = "BEARISH"

        else:

            bias = "NEUTRAL"

        return {
            "call_total_oi": call_oi,
            "put_total_oi": put_oi,
            "bias": bias
        }