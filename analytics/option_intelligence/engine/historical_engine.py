from __future__ import annotations


class HistoricalEngine:

    def __init__(self):
        self.pcr_history = []

    def calculate(self, pcr, spot, symbol):

        pcr_val = pcr.get("pcr", 1.0)

        self.pcr_history.append(pcr_val)

        if len(self.pcr_history) > 20:
            self.pcr_history.pop(0)

        avg_pcr = sum(self.pcr_history) / len(self.pcr_history)

        if avg_pcr > 1.2:
            regime = "BULLISH_POSITIONING"

        elif avg_pcr < 0.8:
            regime = "BEARISH_POSITIONING"

        else:
            regime = "BALANCED"

        return {
            "symbol": symbol,
            "avg_pcr": round(avg_pcr, 3),
            "regime": regime,
            "samples": len(self.pcr_history)
        }
        