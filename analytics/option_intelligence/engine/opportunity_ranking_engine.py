from __future__ import annotations
from typing import Dict, Any


class OpportunityRankingEngine:
    """
    Institutional-grade opportunity scoring engine.

    Purpose:
    - Rank trade quality (HIGH / MEDIUM / LOW)
    - Filter weak setups before execution layer

    Inputs:
    - confidence score
    - gamma positioning
    - volume spikes
    - compression (flip distance)

    Output:
    - opportunity score (0–100)
    - priority (HIGH / MEDIUM / LOW)
    """

    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        # -----------------------------
        # 1. CONFIDENCE (PRIMARY DRIVER)
        # -----------------------------
        confidence = data.get("confidence", {}).get("confidence_score", 0)

        # -----------------------------
        # 2. GAMMA (MARKET FORCE)
        # -----------------------------
        dealer_model = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        net_gamma = abs(dealer_model.get("net_gamma", 0))

        # Normalize gamma (cap to avoid distortion)
        gamma_score = min(net_gamma / 200, 1.0) * 100

        # -----------------------------
        # 3. VOLUME (SMART MONEY ACTIVITY)
        # -----------------------------
        spikes = data.get("institutional_flow", {}) \
            .get("volume_spike_engine", {}) \
            .get("spikes", [])

        volume_score = min(len(spikes) * 10, 100)

        # -----------------------------
        # 4. COMPRESSION (EXPLOSIVE SETUP)
        # -----------------------------
        compression_data = data.get("market_structure", {}).get("compression", {})
        flip_distance = compression_data.get("flip_distance_pct", 1)

        # Lower flip distance = higher score
        compression_score = max(0, (1 - flip_distance)) * 100

        # -----------------------------
        # 5. FINAL WEIGHTED SCORE
        # -----------------------------
        score = (
            confidence * 0.4 +
            gamma_score * 0.3 +
            volume_score * 0.2 +
            compression_score * 0.1
        )

        # -----------------------------
        # 6. PRIORITY CLASSIFICATION
        # -----------------------------
        if score >= 80:
            priority = "HIGH"
        elif score >= 60:
            priority = "MEDIUM"
        else:
            priority = "LOW"

        # -----------------------------
        # 7. OUTPUT
        # -----------------------------
        return {
            "opportunity": {
                "score": round(score, 2),
                "priority": priority,
                "components": {
                    "confidence": confidence,
                    "gamma_score": round(gamma_score, 2),
                    "volume_score": volume_score,
                    "compression_score": round(compression_score, 2)
                },
                "reason": "Weighted scoring (confidence + gamma + volume + compression)"
            }
        }
        