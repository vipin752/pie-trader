class EntryTiming:

    def check(self, signal):
        if signal == "BREAKOUT":
            return "ENTER_NOW"
        return "WAIT"
    