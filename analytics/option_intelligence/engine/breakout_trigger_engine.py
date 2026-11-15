class BreakoutTriggerEngine:

    def detect(self, ctx):

        market_ctx = ctx.get("market_context", {})
        spot = market_ctx.get("spot", 0)

        sr = ctx.get("liquidity_map", {}).get("support_resistance", {})
        support = sr.get("support", spot - 50)
        resistance = sr.get("resistance", spot + 50)

        price_action = ctx.get("price_action", {})
        prev_spot = price_action.get("previous", spot)

        spikes = ctx.get("institutional_flow", {}).get("volume_spike_engine", {}).get("spikes", [])

        buffer = max(5, abs(resistance - support) * 0.02)

        # =========================
        # 🔥 CROSSING LOGIC (MOST IMPORTANT)
        # =========================

        # DOWN BREAK
        if prev_spot >= support and spot < support:
            return {
                "status": "BREAKDOWN",
                "direction": "DOWN",
                "trigger_price": support,
                "type": "CROSSING"
            }

        # UP BREAK
        if prev_spot <= resistance and spot > resistance:
            return {
                "status": "BREAKOUT",
                "direction": "UP",
                "trigger_price": resistance,
                "type": "CROSSING"
            }

        # =========================
        # 🔥 BUFFER BREAK (fallback)
        # =========================

        if spot < (support - buffer):
            return {
                "status": "BREAKDOWN",
                "direction": "DOWN",
                "trigger_price": support,
                "type": "BUFFER"
            }

        if spot > (resistance + buffer):
            return {
                "status": "BREAKOUT",
                "direction": "UP",
                "trigger_price": resistance,
                "type": "BUFFER"
            }

        # =========================
        # 🔥 VOLUME SPIKE BREAKOUT
        # =========================

        if len(spikes) >= 5:
            direction = "UP" if spot > prev_spot else "DOWN"

            return {
                "status": "BREAKOUT",
                "direction": direction,
                "trigger_price": "VOLUME_SPIKE",
                "type": "VOLUME"
            }

        return {
            "status": "WAIT",
            "direction": None,
            "trigger_price": f"{support}/{resistance}",
            "type": "RANGE"
        }
        