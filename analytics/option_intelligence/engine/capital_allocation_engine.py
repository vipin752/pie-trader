from typing import Dict, Any

# =========================================================
# ✅ 4. CAPITAL ALLOCATION ENGINE
# =========================================================
class CapitalAllocationEngine:

    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        confidence = data.get("confidence", {}).get("confidence_score", 0)

        gamma = abs(
            data.get("dealer_positioning", {})
            .get("dealer_inventory_model", {})
            .get("net_gamma", 0)
        )

        if confidence >= 90 and gamma > 100:
            allocation = 5.0
            reason = "High conviction (gamma + confidence)"
        elif confidence >= 75:
            allocation = 2.0
            reason = "Moderate conviction"
        else:
            allocation = 0.5
            reason = "Low conviction"

        return {
            "capital_allocation": {
                "allocation_pct": allocation,
                "reason": reason
            }
        }
