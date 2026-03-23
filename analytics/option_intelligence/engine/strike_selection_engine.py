from typing import Dict, Any, List


class StrikeSelectionEngine:

    def select(self, ctx: Dict[str, Any]) -> Dict[str, Any]:

        strikes = ctx.get("dealer_positioning", {}).get("gamma", {}).get("strikes_enriched", [])
        premium = ctx.get("premium_intelligence", {})
        spot = ctx.get("market_context", {}).get("spot", 0)

        if not strikes or premium.get("premium_activity") != "HIGH":
            return self._empty("No premium edge")

        # 🔥 DIRECTION FROM PRESSURE
        pressure = ctx.get("market_structure", {}).get("pressure", {})
        pressure_value = pressure.get("pressure", 0)

        direction = None
        if pressure_value > 10:
            direction = "UP"
        elif pressure_value < -10:
            direction = "DOWN"

        scored = []

        for s in strikes:
            score = self._score_strike(s, spot)
            if score > 0:

                option_type = self._option_type(s, spot, direction)

                scored.append({
                    "strike": s.get("strike"),
                    "type": option_type,
                    "score": score,
                    "ltp": self._ltp(s, option_type),
                    "volume": s.get("call_volume", 0) + s.get("put_volume", 0),
                    "iv": (s.get("call_iv", 0) + s.get("put_iv", 0)) / 2
                })

        if not scored:
            return self._empty("No valid strikes")

        scored.sort(key=lambda x: x["score"], reverse=True)

        best = scored[0]

        return {
            "selected_strike": f"{best['strike']} {best['type']}",
            "confidence": self._confidence(best["score"]),
            "reason": "Best gamma + volume + proximity",
            "top_candidates": scored[:5]
        }

    # =========================
    # STRIKE SCORING (UNCHANGED)
    # =========================
    def _score_strike(self, s: Dict[str, Any], spot: float) -> float:

        strike = s.get("strike", 0)
        distance = abs(strike - spot)

        # ❌ avoid far OTM
        if distance > 200:
            return 0

        call_vol = s.get("call_volume", 0)
        put_vol = s.get("put_volume", 0)

        call_iv = s.get("call_iv", 0)
        put_iv = s.get("put_iv", 0)

        gamma = s.get("call_gamma", 0) + s.get("put_gamma", 0)

        # =========================
        # COMPONENT SCORES
        # =========================
        proximity_score = 1 / (distance + 1)
        volume_score = (call_vol + put_vol) / 100000
        iv_score = (call_iv + put_iv) / 50
        gamma_score = gamma * 1000

        total = (
            proximity_score * 0.3 +
            volume_score * 0.3 +
            gamma_score * 0.25 +
            iv_score * 0.15
        )

        return total

    # =========================
    # OPTION TYPE (🔥 FIXED)
    # =========================
    def _option_type(self, s: Dict[str, Any], spot: float, direction: str | None) -> str:

        strike = s.get("strike", 0)

        # 🔥 PRIMARY: direction-driven
        if direction == "UP":
            return "CE"
        elif direction == "DOWN":
            return "PE"

        # 🔁 FALLBACK (original behavior)
        return "CE" if strike >= spot else "PE"

    # =========================
    # LTP PICK (FIXED SAFE)
    # =========================
    def _ltp(self, s: Dict[str, Any], option_type: str) -> float:

        if option_type == "CE":
            return s.get("call_ltp", 0)
        else:
            return s.get("put_ltp", 0)

    # =========================
    # CONFIDENCE (UNCHANGED)
    # =========================
    def _confidence(self, score: float) -> str:
        if score > 1.5:
            return "HIGH"
        elif score > 1:
            return "MEDIUM"
        return "LOW"

    # =========================
    # EMPTY (UNCHANGED)
    # =========================
    def _empty(self, reason: str):
        return {
            "selected_strike": None,
            "confidence": "LOW",
            "reason": reason,
            "top_candidates": []
        }
        