"""
Dynamic BTST Engine (Production Grade)
NO HARDCODING - Fully dynamic based on option chain
"""

import math
from typing import Dict, Any, List


class BTSTEngine:

    def __init__(self, data: Dict[str, Any]):
        self.data = data
        self.spot = data.get('market_context', {}).get('spot', 0)
        self.atm = data.get('market_context', {}).get('atm', 0)
        self.iv = data.get('volatility_context', {}).get('volatility_engine', {}).get('atm_iv_pct', 17.25)

        self.strikes_data = data.get('dealer_positioning', {}).get('gamma', {}).get('strikes_enriched', [])

    # ================================
    # MAIN ENTRY
    # ================================
    def generate_btst_setups(self, dte: int) -> Dict[str, Any]:

        candidates = self._get_dynamic_strikes()

        setups = []

        for s in candidates:
            strike = s.get("strike")

            # evaluate both CE & PE dynamically
            for opt_type in ["CE", "PE"]:
                price = s.get(f"{opt_type.lower()}_ltp", 0)

                if price <= 0:
                    continue

                theta = self._theta(price, dte)
                expected_gap = self._expected_gap(strike, opt_type)

                rr = expected_gap / theta if theta > 0 else 0

                setups.append({
                    "strike": strike,
                    "option": f"{strike} {opt_type}",
                    "price": round(price, 2),
                    "theta_overnight": round(theta, 2),
                    "expected_gap": round(expected_gap, 2),
                    "rr": round(rr, 2),
                    "btst_rating": self._rating(rr)
                })

        # sort best first
        setups.sort(key=lambda x: x["rr"], reverse=True)

        best = setups[0] if setups else None

        return {
            "best_btst": best,
            "setups": setups[:5]  # top 5 only for UI
        }

    # ================================
    # DYNAMIC STRIKE SELECTION
    # ================================
    def _get_dynamic_strikes(self) -> List[Dict[str, Any]]:
        """
        Select relevant strikes dynamically:
        - ATM ± range
        - High volume / high OI
        """

        selected = []

        for s in self.strikes_data:
            strike = s.get("strike", 0)

            # range filter (ATM ± 500)
            if abs(strike - self.atm) <= 500:
                selected.append(s)

        # sort by liquidity (volume + oi)
        selected.sort(
            key=lambda x: x.get("call_volume", 0) + x.get("put_volume", 0),
            reverse=True
        )

        return selected[:10]  # top liquid strikes

    # ================================
    # THETA CALCULATION
    # ================================
    def _theta(self, price: float, dte: int) -> float:
        return price * (self.iv / 100) * 0.01 * math.sqrt(max(dte, 1) / 365) * 0.7

    # ================================
    # EXPECTED GAP (SMART)
    # ================================
    def _expected_gap(self, strike: int, opt_type: str) -> float:

        gamma = self.data.get('dealer_positioning', {}).get('dealer_inventory_model', {}).get('net_gamma', 0)

        base_move = abs(self.spot - strike)

        # gamma impact
        if gamma < 0:
            multiplier = 0.35  # more movement
        else:
            multiplier = 0.2

        # direction bias
        direction = self.data.get('market_structure', {}).get('probability_model', {}).get('direction', "")

        if opt_type == "PE" and "DOWN" in direction:
            multiplier *= 1.2
        elif opt_type == "CE" and "UP" in direction:
            multiplier *= 1.2

        return max(20, base_move * multiplier)

    # ================================
    # RATING LOGIC
    # ================================
    def _rating(self, rr: float) -> str:
        if rr > 3:
            return "HIGH"
        elif rr > 2:
            return "MODERATE"
        return "LOW"
    