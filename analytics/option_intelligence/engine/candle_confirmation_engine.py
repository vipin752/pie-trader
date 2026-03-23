class CandleConfirmationEngine:

    def confirm(self, breakout, ctx):

        if breakout.get("status") not in ["BREAKOUT", "BREAKDOWN"]:
            return {
                "confirmed": False,
                "type": "NONE",
                "message": "No breakout"
            }

        pressure = ctx.get("market_structure", {}).get("pressure", {}).get("pressure", 0)
        spikes = ctx.get("institutional_flow", {}).get("volume_spike_engine", {}).get("spikes", [])

        # 🔥 MULTI-CONDITION CONFIRMATION
        if abs(pressure) > 40 or len(spikes) >= 3:
            return {
                "confirmed": True,
                "type": "STRONG_CONFIRMATION",
                "message": "Pressure + volume aligned"
            }

        return {
            "confirmed": False,
            "type": "WEAK",
            "message": "Waiting confirmation"
        }
        