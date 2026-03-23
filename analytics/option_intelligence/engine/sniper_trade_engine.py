class SniperTradeEngine:

    def generate(self, ctx, option):

        if not option:
            return {"action": "NO_TRADE"}

        entry_price = option["price"]

        return {
            "strategy": "SNIPER_BUY",
            "option": f"{option['strike']} {'CE' if ctx['direction']=='UP' else 'PE'}",
            "entry_price": entry_price,

            # 🎯 YOUR CORE GOAL
            "target_price": entry_price + 25,
            "sl_price": max(entry_price - 15, 5),

            "lot_size": 100,
            "expected_move": "₹20-₹30",
            "hold_time": "15-45 min",

            "confidence": "HIGH"
        }
        