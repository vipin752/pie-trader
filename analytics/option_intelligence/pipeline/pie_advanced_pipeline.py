from __future__ import annotations
from typing import Dict, Any


# =========================================================
# ✅ EXISTING ENGINE (DO NOT MODIFY)
# =========================================================
class MarketRegimeEngine:

    def detect(self, spot, gamma, gex, compression, volatility):

        gamma_regime = gex.get("gamma_regime")

        compressed = compression.get("compression_detected", False)

        daily_move = volatility.get("daily_move", 0)

        call_wall = gamma.get("call_gamma_wall", 0)
        put_wall = gamma.get("put_gamma_wall", 0)

        width = abs(call_wall - put_wall)

        if compressed:
            regime = "GAMMA_COMPRESSION"

        elif gamma_regime == "positive_gamma":
            regime = "RANGE_DAY"

        elif gamma_regime == "negative_gamma":
            regime = "TREND_DAY"

        else:
            regime = "NEUTRAL"

        if width < daily_move * 0.5:
            volatility_state = "LOW_VOL"
        else:
            volatility_state = "EXPANDING_VOL"

        return {
            "market_regime": regime,
            "volatility_state": volatility_state,
            "range_width": width,
            "expected_daily_move": daily_move
        }


# =========================================================
# ✅ NEW WRAPPER (NO REGRESSION)
# =========================================================
class MarketRegimeEvaluator:

    @staticmethod
    def evaluate(data: Dict[str, Any], regime_output: Dict[str, Any]) -> Dict[str, Any]:

        is_market = data["market_context"]["session"]["is_market"]

        fake_breakout = data.get("execution_debug", {}) \
            .get("fake_breakout", {}) \
            .get("is_fake_breakout", False)

        regime = regime_output.get("market_regime")
        volatility_state = regime_output.get("volatility_state")

        if not is_market:
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Market Closed",
                    "regime": regime
                }
            }

        if fake_breakout:
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Fake Breakout",
                    "regime": regime
                }
            }

        if volatility_state == "LOW_VOL" and regime != "GAMMA_COMPRESSION":
            return {
                "market_regime_filter": {
                    "trade_allowed": False,
                    "reason": "Low Volatility Trap",
                    "regime": regime
                }
            }

        return {
            "market_regime_filter": {
                "trade_allowed": True,
                "reason": "Favorable Regime",
                "regime": regime
            }
        }


# =========================================================
# ✅ OPPORTUNITY RANKING ENGINE
# =========================================================
class OpportunityRankingEngine:

    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        probability = data.get("confidence", {}).get("confidence_score", 0)

        gamma = abs(
            data.get("dealer_positioning", {})
            .get("dealer_inventory_model", {})
            .get("net_gamma", 0)
        )

        spikes = data.get("institutional_flow", {}) \
            .get("volume_spike_engine", {}) \
            .get("spikes", [])

        volume_score = min(len(spikes) * 10, 100)

        flip_distance = data.get("market_structure", {}) \
            .get("compression", {}) \
            .get("flip_distance_pct", 1)

        score = (
            probability * 0.4 +
            min(gamma / 200, 100) * 0.3 +
            volume_score * 0.2 +
            (1 - flip_distance) * 100 * 0.1
        )

        if score >= 80:
            priority = "HIGH"
        elif score >= 60:
            priority = "MEDIUM"
        else:
            priority = "LOW"

        return {
            "opportunity": {
                "score": round(score, 2),
                "priority": priority,
                "reason": "Probability + Gamma + Volume + Compression"
            }
        }


# =========================================================
# ✅ STRIKE OPTIMIZER
# =========================================================
class StrikeOptimizer:

    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        selected = data["strike_selection"]["selected_strike"]

        prob_data = data.get("market_structure", {}) \
            .get("probability_model", {}) \
            .get("afternoon_session_13_45_14_45", {})

        prob_10x = prob_data.get("probabilities", {}).get("10x", 0)
        best_10x = prob_data.get("best_strike_10x")

        final_strike = selected
        reason = "Default Strike"

        if best_10x and prob_10x >= 25:
            final_strike = best_10x
            reason = "10x Probability Override"

        return {
            "optimized_strike": {
                "final_strike": final_strike,
                "reason": reason,
                "probability_10x": prob_10x
            }
        }


# =========================================================
# ✅ CAPITAL ALLOCATION ENGINE
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
            reason = "High Conviction"
        elif confidence >= 75:
            allocation = 2.0
            reason = "Moderate Conviction"
        else:
            allocation = 0.5
            reason = "Low Conviction"

        return {
            "capital_allocation": {
                "allocation_pct": allocation,
                "reason": reason
            }
        }


# =========================================================
# ✅ PRE-MARKET ENGINE
# =========================================================
class PreMarketBreakoutEngine:

    @staticmethod
    def evaluate(data: Dict[str, Any]) -> Dict[str, Any]:

        session = data["market_context"]["session"]["session"]

        compression = data.get("market_structure", {}) \
            .get("compression", {}) \
            .get("compression_detected", False)

        if session == "PRE_MARKET" and compression:
            return {
                "pre_market_plan": {
                    "action": "PREPARE",
                    "reason": "Compression before open",
                    "expected_move": "EXPLOSIVE"
                }
            }

        return {
            "pre_market_plan": {
                "action": "WAIT",
                "reason": "No setup"
            }
        }


# =========================================================
# 🚀 FINAL ORCHESTRATOR (SAFE, NO REGRESSION)
# =========================================================
class PIEAdvancedPipeline:

    def __init__(self):
        self.regime_engine = MarketRegimeEngine()

    def run(self, data: Dict[str, Any]) -> Dict[str, Any]:

        # ---------------------------
        # 1. EXISTING REGIME ENGINE
        # ---------------------------
        regime_output = self.regime_engine.detect(
            data["market_context"]["spot"],
            data["dealer_positioning"]["gamma"],
            data["dealer_positioning"]["gex"],
            data["market_structure"]["compression"],
            data["volatility_context"]["volatility_engine"]
        )

        # ---------------------------
        # 2. REGIME FILTER (NEW)
        # ---------------------------
        regime_filter = MarketRegimeEvaluator.evaluate(data, regime_output)

        if not regime_filter["market_regime_filter"]["trade_allowed"]:
            return regime_filter  # SAFE EARLY EXIT

        data.update(regime_output)
        data.update(regime_filter)

        # ---------------------------
        # 3. OPPORTUNITY
        # ---------------------------
        opp = OpportunityRankingEngine.evaluate(data)

        if opp["opportunity"]["priority"] != "HIGH":
            return opp  # SAFE FILTER

        data.update(opp)

        # ---------------------------
        # 4. STRIKE OPTIMIZATION
        # ---------------------------
        strike = StrikeOptimizer.evaluate(data)
        data.update(strike)

        # ---------------------------
        # 5. PRE-MARKET PLAN
        # ---------------------------
        pre = PreMarketBreakoutEngine.evaluate(data)
        data.update(pre)

        # ---------------------------
        # 6. CAPITAL
        # ---------------------------
        capital = CapitalAllocationEngine.evaluate(data)
        data.update(capital)

        return data
    