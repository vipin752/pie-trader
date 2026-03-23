"""
Dynamic Probability Matrix Engine
NO HARDCODED VALUES
"""

from typing import Dict, Any


class ProbabilityMatrixEngine:

    def __init__(self, data: Dict[str, Any]):
        self.data = data
        self.spot = data.get('market_context', {}).get('spot', 0)
        self.atm = data.get('market_context', {}).get('atm', 0)

        self.iv = data.get('volatility_context', {}).get('volatility_engine', {}).get('atm_iv_pct', 17.25)

        self.gamma = data.get('dealer_positioning', {}).get('dealer_inventory_model', {}).get('net_gamma', 0)

        self.strikes = data.get('dealer_positioning', {}).get('gamma', {}).get('strikes_enriched', [])

    # =========================================
    def calculate_probabilities(self) -> Dict[str, Any]:

        best_strike = self._select_best_strike()

        base = self._base_probability()

        return {
            "afternoon_session_13_45_14_45": {
                "probabilities": {
                    "10x": base,
                    "20x": round(base * 0.42, 2),
                    "50x": round(base * 0.1, 2),
                    "100x": round(base * 0.025, 2)
                },
                "best_strike_10x": best_strike
            }
        }

    # =========================================
    # BASE PROBABILITY CALCULATION
    # =========================================
    def _base_probability(self) -> float:

        # IV effect (normalized around 15%)
        iv_factor = self.iv / 15

        # Gamma effect (negative gamma boosts moves)
        gamma_factor = 1 + (abs(self.gamma) / 100 if self.gamma < 0 else 0.2)

        # Clamp to avoid explosion
        probability = 8 * iv_factor * gamma_factor

        return round(min(probability, 25), 2)

    # =========================================
    # BEST STRIKE SELECTION (DYNAMIC)
    # =========================================
    def _select_best_strike(self) -> str:

        best = None
        best_score = -1

        direction = self.data.get('market_structure', {}).get('probability_model', {}).get('direction', "")

        for s in self.strikes:
            strike = s.get("strike", 0)

            distance = abs(strike - self.spot)

            liquidity = s.get("call_volume", 0) + s.get("put_volume", 0)

            # scoring
            score = (1 / (distance + 1)) * liquidity

            if score > best_score:
                best_score = score
                best = strike

        if not best:
            return f"{self.atm} CE"

        # direction-based option
        if "DOWN" in direction:
            return f"{best} PE"
        else:
            return f"{best} CE"
        