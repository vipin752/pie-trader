from typing import Dict, Any


class TradeSignalEngine:

    def generate(self, ctx: Dict[str, Any]) -> Dict[str, Any]:

        pressure = ctx.get("market_structure", {}).get("pressure", {})
        compression = ctx.get("market_structure", {}).get("compression", {})
        gamma = ctx.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        window = ctx.get("trading_window", {}).get("window", "UNKNOWN")

        pressure_value = pressure.get("pressure", 0)
        compression_flag = compression.get("compression_detected", False)
        gamma_type = gamma.get("dealer_inventory", "NEUTRAL")

        # =========================
        # 🔥 DO NOT BLOCK EXECUTION
        # =========================

        # EXPANSION = READY STATE (not block)
        if window == "EXPANSION" and compression_flag:
            return {
                "strategy": "PREPARE",
                "reason": "Compression + expansion → breakout ready",
                "confidence": "HIGH"
            }

        if compression_flag:
            return {
                "strategy": "PREPARE",
                "reason": "Compression detected",
                "confidence": "HIGH"
            }

        # TREND FOLLOW
        if pressure_value > 60 and gamma_type == "LONG_GAMMA":
            return {
                "strategy": "TREND_BUY",
                "reason": "Bullish pressure + long gamma",
                "confidence": "MEDIUM"
            }

        if pressure_value < 40 and gamma_type == "SHORT_GAMMA":
            return {
                "strategy": "TREND_SELL",
                "reason": "Bearish pressure + short gamma",
                "confidence": "MEDIUM"
            }

        return {
            "strategy": "WAIT",
            "reason": "No edge",
            "confidence": "LOW"
        }
        