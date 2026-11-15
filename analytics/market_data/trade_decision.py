class TradeDecision:

    def decide(self, data):
        if data["confidence"] > 70:
            return "EXECUTE"
        elif data["confidence"] > 50:
            return "PREPARE"
        return "WAIT"
    