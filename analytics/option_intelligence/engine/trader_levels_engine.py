class TraderLevelsEngine:

    def analyze(self, spot, levels, gamma_flip):

        signals = []

        pdh = levels.get("prev_day_high")
        pdl = levels.get("prev_day_low")

        wh = levels.get("weekly_high")
        wl = levels.get("weekly_low")

        mh = levels.get("monthly_high")
        ml = levels.get("monthly_low")

        if pdh and spot > pdh:
            signals.append("PDH_BREAKOUT")

        if pdl and spot < pdl:
            signals.append("PDL_BREAKDOWN")

        if wh and spot > wh:
            signals.append("WEEKLY_BREAKOUT")

        if wl and spot < wl:
            signals.append("WEEKLY_BREAKDOWN")

        if gamma_flip:

            if abs(spot - gamma_flip) < 30:
                signals.append("AT_GAMMA_PIVOT")

        return {
            "levels": levels,
            "signals": signals
        }
        