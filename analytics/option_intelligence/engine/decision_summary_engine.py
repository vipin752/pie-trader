class DecisionSummaryEngine:

    def generate(self, ctx):

        window = ctx["trading_window"]["window"]
        gamma = ctx["dealer_positioning"]["dealer_inventory_model"]["dealer_inventory"]
        compression = ctx["market_structure"]["compression"]["compression_detected"]
        direction = ctx["market_structure"]["probability_model"]["direction"]
        spot = ctx["market_context"]["spot"]
        support = ctx["liquidity_map"]["support_resistance"]["support"]
        resistance = ctx["liquidity_map"]["support_resistance"]["resistance"]

        summary = {}
        action = "NO TRADE"
        reason = []

        if window == "OPENING":
            action = "AVOID"
            reason.append("Opening manipulation zone")

        elif window == "THETA":
            if gamma == "LONG_GAMMA":
                action = "SELL PREMIUM"
                reason.append("Range + theta decay active")
            else:
                action = "WAIT"
                reason.append("Breakout possible")

        elif window == "EXPANSION":
            action = "PREPARE BREAKOUT"
            reason.append("Time for directional move")

        if spot <= support:
            trigger = f"Breakdown below {support} → BUY PE"
        elif spot >= resistance:
            trigger = f"Breakout above {resistance} → BUY CE"
        else:
            trigger = f"Range: {support} – {resistance}"

        return {
            "action": action,
            "reason": ", ".join(reason),
            "market_state": window,
            "gamma": gamma,
            "direction_bias": direction,
            "levels": {
                "support": support,
                "resistance": resistance
            },
            "trigger": trigger
        }
        