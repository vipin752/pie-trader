from __future__ import annotations
from typing import Dict, Any


class ProbabilityEngine:

    def calculate(
        self,
        spot: float,
        gamma: Dict,
        pcr: Dict,
        gex: Dict,
        ctx: Dict = None
    ) -> Dict[str, Any]:

        # ================= SAFETY =================
        if not gamma or not pcr or not gex:
            return {}

        # ❗ HARD BLOCK (Fake breakout)
        if ctx and ctx.get("execution_debug", {}).get("fake_breakout", {}).get("is_fake_breakout"):
            return {}

        # ================= INPUTS =================
        net_gamma = gamma.get("net_gamma", 0)
        gamma_flip = gamma.get("gamma_flip", spot)
        pcr_value = pcr.get("pcr", 1)

        regime = gex.get("gamma_regime", "neutral_gamma")

        # ================= DIRECTION =================
        direction = self._direction(net_gamma, spot, gamma_flip, pcr_value)

        # ================= BASE PROBABILITIES =================
        base_probs = self._base_probabilities(direction, regime)

        # ================= ADJUSTMENT =================
        adjusted_probs = self._adjust_probabilities(
            base_probs,
            pcr_value,
            net_gamma,
            regime
        )

        # ================= STRIKE SELECTION =================
        best_strike = self._select_strike(
            spot,
            direction,
            gamma
        )

        return {
            "afternoon_session_13_45_14_45": {
                "probabilities": adjusted_probs,
                "best_strike_10x": best_strike
            }
        }

    # =========================================================
    # DIRECTION LOGIC
    # =========================================================
    def _direction(self, net_gamma, spot, gamma_flip, pcr):

        if net_gamma < 0:
            if spot < gamma_flip:
                return "DOWNSIDE_BIAS"
            else:
                return "UPSIDE_VOLATILITY"

        if pcr > 1.2:
            return "UPSIDE_BIAS"

        if pcr < 0.8:
            return "DOWNSIDE_BIAS"

        return "NEUTRAL"

    # =========================================================
    # BASE PROBABILITIES
    # =========================================================
    def _base_probabilities(self, direction, regime):

        base = {
            "10x": 10.0,
            "20x": 5.0,
            "50x": 1.2,
            "100x": 0.3
        }

        # gamma regime effect
        if regime == "negative_gamma":
            base["10x"] += 2
            base["20x"] += 1

        # directional bias
        if direction == "DOWNSIDE_BIAS":
            base["10x"] += 0.5
        elif direction == "UPSIDE_BIAS":
            base["10x"] += 0.5

        return base

    # =========================================================
    # ADJUSTMENT LOGIC (NO HARDCODE)
    # =========================================================
    def _adjust_probabilities(self, base, pcr, net_gamma, regime):

        adjusted = base.copy()

        # PCR influence
        pcr_factor = (pcr - 1) * 10

        # gamma intensity
        gamma_factor = abs(net_gamma) * 0.1

        # regime boost
        regime_boost = 1.2 if regime == "negative_gamma" else 1.0

        for key in adjusted:
            adjusted[key] = round(
                max(
                    0,
                    adjusted[key] * regime_boost + pcr_factor + gamma_factor
                ),
                2
            )

        return adjusted

    # =========================================================
    # STRIKE SELECTION (FULLY DYNAMIC)
    # =========================================================
    def _select_strike(self, spot, direction, gamma):

        call_wall = gamma.get("call_gamma_wall")
        put_wall = gamma.get("put_gamma_wall")

        if not call_wall or not put_wall:
            return None

        if direction == "DOWNSIDE_BIAS":
            return f"{put_wall} PE"

        if direction == "UPSIDE_BIAS":
            return f"{call_wall} CE"

        # neutral → closer strike
        if abs(spot - call_wall) < abs(spot - put_wall):
            return f"{call_wall} CE"

        return f"{put_wall} PE"
    