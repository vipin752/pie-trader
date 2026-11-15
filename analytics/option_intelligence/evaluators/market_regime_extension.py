from typing import Dict, Any


class MarketRegimeEvaluator:
    """
    Wrapper on top of existing MarketRegimeEngine.
    Adds TRADE_ALLOWED logic without modifying original engine.
    """

    @staticmethod
    def evaluate(data: Dict[str, Any], regime_output: Dict[str, Any]) -> Dict[str, Any]:

        is_market = data["market_context"]["session"]["is_market"]

        fake_breakout = data.get("execution_debug", {}) \
            .get("fake_breakout", {}) \
            .get("is_fake_breakout", False)

        regime = regime_output.get("market_regime")
        volatility_state = regime_output.get("volatility_state")

        # 🔴 RULES (NO REGRESSION)
        if not is_market:
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Market Closed",
                    "regime": regime
                }
            }

        if fake_breakout:
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Fake Breakout",
                    "regime": regime
                }
            }

        if volatility_state == "LOW_VOL" and regime != "GAMMA_COMPRESSION":
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Low volatility trap",
                    "regime": regime
                }
            }

        # ✅ ALLOW TRADE
        return {
            "market_regime_filter": {
                "trade_allowed": True,
                "reason": "Favorable regime",
                "regime": regime
            }
        }
        