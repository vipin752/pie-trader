from typing import Dict, Any, List


class PremiumIntelligenceEngine:

    def analyze(self, ctx: Dict[str, Any]) -> Dict[str, Any]:

        strikes = ctx.get("dealer_positioning", {}).get("gamma", {}).get("strikes_enriched", [])
        spot = ctx.get("market_context", {}).get("spot", 0)

        if not strikes:
            return self._empty()

        scored = []

        for s in strikes:
            score = self._score_strike(s, spot)
            if score > 0:
                scored.append({
                    "strike": s.get("strike"),
                    "score": score,
                    "call_ltp": s.get("call_ltp"),
                    "put_ltp": s.get("put_ltp"),
                    "call_iv": s.get("call_iv"),
                    "put_iv": s.get("put_iv"),
                    "call_volume": s.get("call_volume"),
                    "put_volume": s.get("put_volume")
                })

        if not scored:
            return self._empty()

        # sort by best premium activity
        scored.sort(key=lambda x: x["score"], reverse=True)

        best = scored[0]

        return {
            "premium_activity": "HIGH",
            "best_strike": best["strike"],
            "confidence": self._confidence(best["score"]),
            "top_strikes": scored[:5]
        }

    # =========================
    # STRIKE SCORING LOGIC
    # =========================
    def _score_strike(self, s: Dict[str, Any], spot: float) -> float:

        strike = s.get("strike", 0)

        distance = abs(strike - spot)

        # ❌ avoid deep OTM
        if distance > 300:
            return 0

        call_vol = s.get("call_volume", 0)
        put_vol = s.get("put_volume", 0)

        call_iv = s.get("call_iv", 0)
        put_iv = s.get("put_iv", 0)

        call_ltp = s.get("call_ltp", 0)
        put_ltp = s.get("put_ltp", 0)

        # =========================
        # PREMIUM BUILDUP SCORE
        # =========================
        volume_score = (call_vol + put_vol) / 10000

        iv_score = (call_iv + put_iv) / 50

        ltp_score = (call_ltp + put_ltp) / 500

        distance_score = 1 / (distance + 1)

        total_score = (
            volume_score * 0.4 +
            iv_score * 0.3 +
            ltp_score * 0.2 +
            distance_score * 0.1
        )

        return total_score

    # =========================
    # CONFIDENCE
    # =========================
    def _confidence(self, score: float) -> str:
        if score > 2:
            return "HIGH"
        elif score > 1:
            return "MEDIUM"
        return "LOW"

    # =========================
    # EMPTY RESPONSE
    # =========================
    def _empty(self):
        return {
            "premium_activity": "LOW",
            "reason": "No IV expansion + no dominant strike volume",
            "best_strike": None,
            "confidence": "LOW",
            "top_strikes": []
        }
        