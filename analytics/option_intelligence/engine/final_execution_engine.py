from typing import Dict, Any


class FinalExecutionEngine:

    def decide(self, ctx: Dict[str, Any], rows, strike_engine, sniper_engine) -> Dict[str, Any]:

        breakout = ctx.get("breakout", {})
        confirmation = ctx.get("confirmation", {})
        pressure = ctx.get("market_structure", {}).get("pressure", {})
        window = ctx.get("trading_window", {}).get("window", "")

        session = ctx.get("market_context", {}).get("session", {})
        spot = ctx.get("market_context", {}).get("spot", 0)

        # =========================
        # RULE 1: MARKET CLOSED
        # =========================
        if not session.get("is_market", False):
            return {
                "execution_ready": False,
                "reason": "Market closed"
            }

        # =========================
        # RULE 2: VALID WINDOW
        # =========================
        if window not in ["EXPANSION", "BREAKOUT"]:
            return {
                "execution_ready": False,
                "reason": f"Invalid window: {window}"
            }

        # =========================
        # RULE 3: BREAKOUT REQUIRED
        # =========================
        breakout_status = breakout.get("status")
        if breakout_status not in ["BREAKOUT", "BREAKDOWN"]:
            return {
                "execution_ready": False,
                "reason": "No breakout"
            }

        # =========================
        # RULE 4: CONFIRMATION REQUIRED
        # =========================
        if not confirmation.get("confirmed"):
            return {
                "execution_ready": False,
                "reason": "No confirmation"
            }

        # =========================
        # RULE 5: PRESSURE FILTER
        # =========================
        pressure_value = pressure.get("pressure", 0)

        if abs(pressure_value) < 25:
            return {
                "execution_ready": False,
                "reason": "Weak pressure"
            }

        # =========================
        # RULE 6: DIRECTION (FIXED)
        # =========================
        direction = breakout.get("direction")

        # 🔥 HANDLE AUTO / NONE CASE (NSE DELAY FIX)
        if direction not in ["UP", "DOWN"]:

            # fallback using price action
            price_action = ctx.get("price_action", {})
            change = price_action.get("change", 0)

            if change > 0:
                direction = "UP"
            elif change < 0:
                direction = "DOWN"
            else:
                return {
                    "execution_ready": False,
                    "reason": "Invalid direction"
                }

        # =========================
        # RULE 7: STRIKE SELECTION
        # =========================
        try:
            strike_data = strike_engine.select(ctx)
        except Exception as e:
            return {
                "execution_ready": False,
                "reason": f"Strike selection error: {str(e)}"
            }

        if not strike_data or not strike_data.get("selected_strike"):
            return {
                "execution_ready": False,
                "reason": "No strike selected"
            }

        # =========================
        # RULE 8: EXECUTION PRICING
        # =========================
        try:
            trade = sniper_engine.calculate(ctx)
        except Exception:
            trade = {}

        # =========================
        # FINAL BUILD (UNCHANGED STRUCTURE)
        # =========================
        return {
            "execution_ready": True,
            "direction": direction,
            "option": strike_data.get("selected_strike"),
            "strategy": "BREAKOUT_EXECUTION",
            "entry_price": trade.get("entry_price"),
            "target_price": trade.get("target_price"),
            "sl_price": trade.get("sl_price"),
            "confidence": "HIGH",
            "reason": f"{direction} breakout + confirmation + pressure aligned"
        }
        