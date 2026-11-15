class WriterPressureEngine:

    def detect(self, option_chain):

        call_change = sum([s.call_change_oi for s in option_chain.strikes])
        put_change = sum([s.put_change_oi for s in option_chain.strikes])

        if put_change > call_change:
            return "BULLISH"

        if call_change > put_change:
            return "BEARISH"

        return "NEUTRAL"
        