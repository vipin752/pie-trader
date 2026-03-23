from typing import Dict, Any, Optional


class StrikeOptimizer:
    """
    Institutional-grade Strike Optimizer

    Enhances base strike selection using:
    - Probability model (10x → 1000x)
    - Market pressure direction
    - Flow dominance override

    Designed for:
    - Stability (no regression)
    - Extensibility (ML / adaptive learning ready)
    """

    # =========================
    # MAIN ENTRY
    # =========================
    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        # =========================
        # BASE STRIKE
        # =========================
        strike_data = data.get("strike_selection", {})
        selected: Optional[str] = strike_data.get("selected_strike")

        if not selected:
            return StrikeOptimizer._empty("No base strike")

        # =========================
        # PROBABILITY MODEL
        # =========================
        prob_model = (
            data.get("market_structure", {})
            .get("probability_model", {})
            .get("afternoon_session_13_45_14_45", {})
        )

        probabilities = prob_model.get("probabilities", {})
        best_10x: Optional[str] = prob_model.get("best_strike_10x")

        prob_10x = float(probabilities.get("10x", 0))
        prob_20x = float(probabilities.get("20x", 0))
        prob_50x = float(probabilities.get("50x", 0))
        prob_100x = float(probabilities.get("100x", 0))

        # =========================
        # MARKET DIRECTION
        # =========================
        direction = StrikeOptimizer._derive_direction(data)

        # =========================
        # DEFAULT STATE
        # =========================
        final_strike = selected
        reason = "Default selection"
        override_type = "NONE"

        # =========================
        # 🔥 PRIORITY 1: FLOW DOMINANCE (10x)
        # =========================
        if best_10x and prob_10x >= 25:
            final_strike = best_10x
            reason = "Overridden by 10x probability (flow dominance)"
            override_type = "FLOW_DOMINANCE"

        # =========================
        # 🔥 PRIORITY 2: 20x CONFIRMATION
        # =========================
        elif best_10x and prob_20x >= 30:
            if StrikeOptimizer._is_direction_valid(best_10x, direction):
                final_strike = best_10x
                reason = "20x probability boost (direction aligned)"
                override_type = "CONFIRMED_MOVE"

        # =========================
        # 🔥 PRIORITY 3: HIGH RR (50x / 100x)
        # =========================
        elif best_10x and (prob_50x >= 20 or prob_100x >= 15):
            if StrikeOptimizer._is_direction_valid(best_10x, direction):
                final_strike = best_10x
                reason = "High RR override (50x/100x)"
                override_type = "HIGH_RR"

        # =========================
        # FINAL OUTPUT
        # =========================
        return {
            "optimized_strike": {
                "base_strike": selected,
                "final_strike": final_strike,
                "reason": reason,
                "override_type": override_type,

                # 🔍 Transparency
                "probability": {
                    "10x": prob_10x,
                    "20x": prob_20x,
                    "50x": prob_50x,
                    "100x": prob_100x
                },

                "direction": direction,

                # 🔥 Debug / future ML hooks
                "meta": {
                    "used_flow_override": override_type == "FLOW_DOMINANCE",
                    "used_direction_filter": override_type in ["CONFIRMED_MOVE", "HIGH_RR"]
                }
            }
        }

    # =========================
    # DIRECTION DERIVATION
    # =========================
    @staticmethod
    def _derive_direction(data: Dict[str, Any]) -> str | None:

        # 🔹 Dealer positioning (PRIMARY - most reliable)
        dealer = (
            data.get("dealer_positioning", {})
            .get("dealer_inventory_model", {})
            .get("dealer_inventory")
        )

        # 🔹 Smart money flow
        flow = (
            data.get("institutional_flow", {})
            .get("smart_money_flow_engine", {})
            .get("atm_flow")
        )

        # 🔹 Probability model
        prob_model = (
            data.get("market_structure", {})
            .get("probability_model", {})
            .get("afternoon_session_13_45_14_45", {})
        )
        best_10x = prob_model.get("best_strike_10x")

        # 🔥 PRIORITY 1: FLOW (MOST POWERFUL)
        if flow == "ATM_PUT_BUYING":
            return "DOWN"
        if flow == "ATM_CALL_BUYING":
            return "UP"

        # 🔥 PRIORITY 2: PROBABILITY STRIKE
        if best_10x:
            if "PE" in best_10x:
                return "DOWN"
            if "CE" in best_10x:
                return "UP"

        # 🔥 PRIORITY 3: DEALER GAMMA
        if dealer == "SHORT_GAMMA":
            return "DOWN"
        if dealer == "LONG_GAMMA":
            return "UP"

        # 🔥 LAST: PRESSURE (fallback only)
        pressure = data.get("market_structure", {}).get("pressure", {})
        pressure_val = pressure.get("pressure", 0)

        if pressure_val > 20:
            return "UP"
        elif pressure_val < -20:
            return "DOWN"

        return None

    # =========================
    # DIRECTION VALIDATION
    # =========================
    @staticmethod
    def _is_direction_valid(strike: str, direction: Optional[str]) -> bool:

        if not direction:
            return True  # neutral → allow

        if direction == "UP" and "CE" in strike:
            return True

        if direction == "DOWN" and "PE" in strike:
            return True

        return False

    # =========================
    # EMPTY RESPONSE
    # =========================
    @staticmethod
    def _empty(reason: str) -> Dict[str, Any]:

        return {
            "optimized_strike": {
                "base_strike": None,
                "final_strike": None,
                "reason": reason,
                "override_type": "NONE",
                "probability": {},
                "direction": None,
                "meta": {}
            }
        }
        