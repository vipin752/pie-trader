class TradeDecisionEngine:

    def decide(self, confidence, signal):
        if signal == "STRONG_BUY" and confidence >= 70:
            return "EXECUTE"
        elif confidence >= 55:
            return "PREPARE"
        return "WAIT"
    