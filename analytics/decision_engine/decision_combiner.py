import time

class DecisionCombiner:

    def build(self, symbol, action, strike, sl, target, confidence):
        return {
            "symbol": symbol,
            "action": action,
            "strike": strike,
            "sl": sl,
            "target": target,
            "confidence": confidence,
            "timestamp": int(time.time())
        }
        