class FlowEngine:

    def detect(self, current, previous):

        signals = []

        for cur, prev in zip(current, previous):

            price_move = cur["strike"] - prev["strike"]

            call_change = cur["call_oi"] - prev["call_oi"]

            if price_move > 0 and call_change > 0:
                signals.append("LONG_BUILDUP")

            if price_move < 0 and call_change > 0:
                signals.append("SHORT_BUILDUP")

        return signals
        