from typing import Dict, Any


class ScalingEngine:

    def scale(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            # =========================
            # SAFE EXTRACTION
            # =========================
            position = data.get("position_management", {}) or {}
            execution = data.get("execution_timing", {}) or {}
            confidence = data.get("confidence", {}) or {}
            gamma = data.get("dealer_positioning", {}).get("gamma", {}) or {}
            flow = data.get("institutional_flow", {}).get("volume_spike_engine", {}) or {}
            probability = data.get("market_structure", {}).get("probability_model", {}) or {}
            compression = data.get("market_structure", {}).get("compression", {}) or {}
            market = data.get("market_context", {}) or {}

            # =========================
            # HARD CONDITIONS
            # =========================
            if position.get("position_status") != "ACTIVE":
                return self._no_scale("No active position")

            entry_signal = execution.get("entry_signal")
            entry_type = execution.get("entry_type")

            if entry_signal not in ["ENTER_LONG", "ENTER_SHORT", "EARLY_ENTRY"]:
                return self._no_scale("Invalid entry state")

            # =========================
            # SAFE VALUES
            # =========================
            confidence_score = float(confidence.get("confidence_score", 0))
            net_gamma = float(gamma.get("net_gamma", 0))
            spikes = flow.get("spikes", []) or []

            prob_data = (
                probability.get("afternoon_session_13_45_14_45", {})
                .get("probabilities", {})
            )

            prob_10x = float(prob_data.get("10x", 0))
            prob_20x = float(prob_data.get("20x", 0))
            prob_50x = float(prob_data.get("50x", 0))
            prob_100x = float(prob_data.get("100x", 0))

            is_compression = compression.get("compression_detected", False)

            spot = float(market.get("spot", 0))

            # =========================
            # EDGE DETECTION
            # =========================
            strong_trend = net_gamma < 0 and len(spikes) >= 5
            ultra_trend = net_gamma < 0 and len(spikes) >= 7 and confidence_score >= 90

            # =========================
            # 🚫 DO NOT SCALE CONDITIONS
            # =========================
            if not strong_trend:
                return self._no_scale("Weak trend")

            if confidence_score < 70:
                return self._no_scale("Low confidence")

            # =========================
            # 💥 MULTIPLIER DETECTION (CORE EDGE)
            # =========================
            multiplier = self._detect_multiplier(
                prob_10x, prob_20x, prob_50x, prob_100x,
                ultra_trend, is_compression
            )

            # =========================
            # SCALE SIZE LOGIC
            # =========================
            scale_fraction = self._scale_size(multiplier)

            # =========================
            # SCALE ACTION
            # =========================
            return {
                "scale_action": "ADD_POSITION",
                "scale_fraction": scale_fraction,   # % of original position
                "multiplier_potential": multiplier,

                "reason": self._build_reason(multiplier, strong_trend, ultra_trend),

                "risk_note": "Scaled only after confirmation",

                "confidence": "HIGH" if ultra_trend else "MEDIUM"
            }

        except Exception as e:
            return self._no_scale(f"Scaling error: {str(e)}")

    # =========================================================
    # MULTIPLIER DETECTION ENGINE (1x → 1000x)
    # =========================================================
    def _detect_multiplier(
        self,
        p10, p20, p50, p100,
        ultra_trend,
        compression
    ):

        # 🚀 EXTREME CASE (RARE — YOUR 1000x TARGET)
        if ultra_trend and compression and p100 >= 15:
            return "1000x"

        # 🔥 HIGH MULTIPLIER
        if ultra_trend and p50 >= 15:
            return "100x"

        # 💰 STRONG MOVE
        if p20 >= 15:
            return "20x"

        # 📈 NORMAL TREND
        if p10 >= 10:
            return "10x"

        return "1x"

    # =========================================================
    # SCALE SIZE BASED ON EDGE
    # =========================================================
    def _scale_size(self, multiplier: str):

        if multiplier == "1000x":
            return 1.0   # double position (max conviction)

        if multiplier == "100x":
            return 0.75

        if multiplier == "20x":
            return 0.5

        if multiplier == "10x":
            return 0.3

        return 0.1

    # =========================================================
    # REASON BUILDER
    # =========================================================
    def _build_reason(self, multiplier, strong, ultra):

        if multiplier == "1000x":
            return "Ultra trend + compression + extreme probability"

        if multiplier == "100x":
            return "Ultra trend + high probability"

        if multiplier == "20x":
            return "Strong trend continuation"

        if multiplier == "10x":
            return "Moderate trend scaling"

        return "Low conviction scaling"

    # =========================================================
    # NO SCALE
    # =========================================================
    def _no_scale(self, reason):

        return {
            "scale_action": "NO_SCALE",
            "reason": reason,
            "confidence": "LOW"
        }
        