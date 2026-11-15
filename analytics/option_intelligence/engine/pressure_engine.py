class PressureEngine:

    def calculate(self, pcr, gamma, gex, probability, compression):

        pressure = 0

        # =========================
        # PCR
        # =========================
        pcr_val = pcr.get("pcr", 1.0)

        if pcr_val > 1.2:
            pressure += 10   # reduced impact
        elif pcr_val < 0.8:
            pressure -= 10

        # =========================
        # GAMMA REGIME (FIXED)
        # =========================
        gamma_regime = gex.get("gamma_regime", "positive_gamma")

        if gamma_regime == "negative_gamma":
            pressure += 10   # smaller weight (not directional)
        else:
            pressure -= 10

        # =========================
        # PROBABILITY (PRIMARY DRIVER)
        # =========================
        direction = probability.get("direction", "NEUTRAL")

        if direction == "STRONG_UPSIDE":
            pressure += 40
        elif direction == "UPSIDE_BIAS":
            pressure += 20
        elif direction == "DOWNSIDE_BIAS":
            pressure -= 20
        elif direction == "STRONG_DOWNSIDE":
            pressure -= 40

        # =========================
        # 🔥 FLOW OVERRIDE (NEW)
        # =========================
        flow = probability.get("best_strike_10x")

        if flow:
            if "PE" in flow:
                pressure -= 15
            elif "CE" in flow:
                pressure += 15

        # =========================
        # COMPRESSION
        # =========================
        if compression.get("compression_detected"):
            pressure += 5

        # clamp
        pressure = max(-100, min(100, pressure))

        # label
        if pressure > 50:
            label = "STRONG_BULLISH"
        elif pressure > 20:
            label = "BULLISH"
        elif pressure < -50:
            label = "STRONG_BEARISH"
        elif pressure < -20:
            label = "BEARISH"
        else:
            label = "NEUTRAL"

        return {
            "pressure": pressure,
            "label": label
        }
        