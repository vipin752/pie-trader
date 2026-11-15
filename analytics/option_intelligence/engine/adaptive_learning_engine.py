from typing import Dict, Any, List


class AdaptiveLearningEngine:

    def __init__(self):
        self.trade_history: List[Dict[str, Any]] = []

        # Default parameters
        self.params = {
            "flip_distance_threshold": 0.12,
            "confidence_threshold": 70,
            "volume_threshold": 5,
            "rr_target": 2.0
        }

    # =========================================================
    # 📊 STORE TRADE RESULT
    # =========================================================
    def record_trade(self, trade_result: Dict[str, Any]):

        if not isinstance(trade_result, dict):
            return

        self.trade_history.append(trade_result)

        # Keep last 100 trades only
        if len(self.trade_history) > 100:
            self.trade_history.pop(0)

    # =========================================================
    # 🧠 ADAPT PARAMETERS
    # =========================================================
    def optimize(self) -> Dict[str, Any]:

        # Not enough data → return current params safely
        if len(self.trade_history) < 20:
            return self.params.copy()

        wins = [t for t in self.trade_history if t.get("profit", 0) > 0]
        losses = [t for t in self.trade_history if t.get("loss", 0) > 0]

        total = len(self.trade_history)
        win_count = len(wins)

        win_rate = win_count / total if total > 0 else 0

        # Safe RR calculation
        avg_rr = (
            sum(t.get("max_rr_hit", 0) for t in wins) / win_count
            if win_count > 0 else 0
        )

        # =====================================================
        # 🔥 ADAPT FLIP DISTANCE
        # =====================================================
        good_trades = [
            t for t in wins
            if t.get("entry_type") == "PRE_BREAKOUT"
        ]

        if good_trades:
            avg_flip = sum(t.get("flip_distance", 0) for t in good_trades) / len(good_trades)

            self.params["flip_distance_threshold"] = round(
                min(max(avg_flip * 1.2, 0.08), 0.20),
                3
            )

        # =====================================================
        # 🔥 ADAPT CONFIDENCE (STABLE)
        # =====================================================
        if win_rate < 0.45:
            self.params["confidence_threshold"] += 3
        elif win_rate > 0.65:
            self.params["confidence_threshold"] -= 3

        self.params["confidence_threshold"] = max(
            50,
            min(90, self.params["confidence_threshold"])
        )

        # =====================================================
        # 🔥 ADAPT RR TARGET
        # =====================================================
        if avg_rr > 2.5:
            self.params["rr_target"] = 2.5
        elif avg_rr < 1.5:
            self.params["rr_target"] = 1.5
        else:
            self.params["rr_target"] = 2.0

        return self.params.copy()

    # =========================================================
    # 🎯 APPLY TO SYSTEM (SAFE — NO SIDE EFFECTS)
    # =========================================================
    def apply_to_execution(self, data: Dict[str, Any]) -> Dict[str, Any]:

        params = self.optimize()

        # Inject safely into pipeline output (NOT engine mutation)
        data["adaptive_params"] = params

        return params
    