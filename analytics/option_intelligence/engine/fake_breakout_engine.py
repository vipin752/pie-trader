class FakeBreakoutEngine:

    def calculate(self, ctx):

        spot = ctx["market_context"]["spot"]
        support = ctx["liquidity_map"]["support_resistance"]["support"]
        resistance = ctx["liquidity_map"]["support_resistance"]["resistance"]

        gamma_regime = ctx["dealer_positioning"]["dealer_inventory_model"]["dealer_inventory"]

        # Simple proxy (you can improve later)
        volume_spike = len(ctx["institutional_flow"]["volume_spike_engine"]["spikes"]) > 5

        fake = False
        reason = ""

        if spot > resistance and not volume_spike:
            fake = True
            reason = "Weak breakout (no volume)"

        if spot > resistance and gamma_regime != "SHORT_GAMMA":
            fake = True
            reason = "Gamma not supportive"

        if spot < support and not volume_spike:
            fake = True
            reason = "Weak breakdown"

        return {
            "is_fake_breakout": fake,
            "reason": reason
        }
        