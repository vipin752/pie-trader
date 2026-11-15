from typing import Dict, Any


class ExitEngine:

    def manage_exit(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            execution = data.get("execution_timing", {})
            risk = data.get("risk_management", {})
            confidence = data.get("confidence", {})
            gamma = data.get("dealer_positioning", {}).get("gamma", {})
            dealer = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
            flow = data.get("institutional_flow", {}).get("volume_spike_engine", {})
            execution_debug = data.get("execution_debug", {})

            entry_signal = execution.get("entry_signal")
            position = risk.get("position", "NO_TRADE")
            position_mode = risk.get("position_mode", "NONE")

            confidence_score = float(confidence.get("confidence_score", 0))
            net_gamma = float(gamma.get("net_gamma", 0))

            spikes = flow.get("spikes") or []
            fake_breakout = execution_debug.get("fake_breakout", {}).get("is_fake_breakout", False)

            # =========================
            # 🚫 NO POSITION → NO EXIT
            # =========================
            if position == "NO_TRADE":
                return self._no_exit("No active position")

            # =========================
            # ❌ INVALID ENTRY
            # =========================
            if entry_signal not in ["ENTER_LONG", "ENTER_SHORT", "ENTER_RANGE", "EARLY_ENTRY"]:
                return self._no_exit("No active trade")

            # =========================
            # 🚨 HARD EXIT
            # =========================
            if fake_breakout:
                return self._full_exit("Fake breakout detected")

            if confidence_score < 35:
                return self._full_exit("Confidence collapse")

            if self._gamma_flip_detected(data):
                return self._full_exit("Gamma regime invalidation")

            # =========================
            # 🧠 MARKET STATE
            # =========================
            strong_trend = self._is_strong_trend(net_gamma, spikes)
            weak_trend = self._is_weakening_trend(net_gamma, spikes)
            range_market = net_gamma > 0

            # =========================
            # 🚀 PILOT EXIT
            # =========================
            if entry_signal == "EARLY_ENTRY" or position_mode in ["PARTIAL_ENTRY", "PILOT"]:

                exit_plan = {
                    "mode": "PILOT_EXIT",

                    "stage_1": {
                        "trigger": "Small favorable move",
                        "action": [
                            "Book 60-70%",
                            "Move SL to cost"
                        ],
                        "logic": "Protect early entry quickly"
                    },

                    "stage_2": {
                        "trigger": "Breakout confirmation",
                        "action": [
                            "Hold remaining",
                            "Convert to full position"
                        ],
                        "logic": "Scale into trend"
                    },

                    "failure_exit": {
                        "condition": "No follow-through",
                        "action": "Exit remaining position",
                        "logic": "Avoid capital erosion"
                    },

                    "trailing": {
                        "mode": "FAST_PROTECTION",
                        "rules": [
                            "Tight SL",
                            "Quick profit booking",
                            "No drawdown allowed"
                        ]
                    }
                }

                return {
                    "exit_plan": exit_plan,
                    "position_state": "MANAGING",
                    "exit_confidence": "HIGH"
                }

            # =========================
            # 🟢 FULL EXIT
            # =========================
            exit_plan = {

                "stage_1": {
                    "trigger": "Initial impulse move",
                    "action": [
                        "Book 30-50%",
                        "Move SL to cost"
                    ],
                    "logic": "Reduce risk early"
                },

                "stage_2": {
                    "trigger": "Momentum continuation",
                    "action": [
                        "Book 20-30%",
                        "Trail SL below structure"
                    ],
                    "logic": "Lock profits"
                },

                "stage_3": {
                    "trigger": "Strong directional move",
                    "action": [
                        "Hold remaining",
                        "Trail aggressively"
                    ],
                    "logic": "Capture big move"
                },

                "smart_trailing": {
                    "mode": self._get_trailing_mode(strong_trend, range_market),
                    "rules": self._build_trailing_rules(strong_trend, weak_trend, range_market)
                },

                "early_exit_signals": {
                    "conditions": [
                        "Volume drying up",
                        "Momentum slowing",
                        "Opposite flow detected",
                        "Rejection at key level"
                    ],
                    "action": "Reduce / exit"
                },

                "emergency_exit": {
                    "conditions": [
                        "Fake breakout",
                        "Gamma flip",
                        "Confidence collapse"
                    ],
                    "action": "EXIT_FULL"
                }
            }

            return {
                "exit_plan": exit_plan,
                "position_state": "MANAGING",
                "exit_confidence": "HIGH" if strong_trend else "MEDIUM"
            }

        except Exception as e:
            return self._no_exit(f"Exit error: {str(e)}")

    def _is_strong_trend(self, net_gamma, spikes):
        return net_gamma < 0 and len(spikes) >= 6

    def _is_weakening_trend(self, net_gamma, spikes):
        return net_gamma < 0 and len(spikes) < 3

    def _get_trailing_mode(self, strong_trend, range_market):
        if strong_trend:
            return "AGGRESSIVE_TRAIL"
        if range_market:
            return "MEAN_REVERSION_EXIT"
        return "NORMAL_TRAIL"

    def _build_trailing_rules(self, strong_trend, weak_trend, range_market):
        if strong_trend:
            return ["Trail below previous candle", "Hold runners", "Exit on strong reversal"]
        if weak_trend:
            return ["Tight trailing", "Book faster profits", "Avoid runners"]
        if range_market:
            return ["Exit near range boundary", "Quick profit booking", "No aggressive trailing"]
        return ["Normal trailing", "Protect gains", "Exit on structure break"]

    def _gamma_flip_detected(self, data):
        gamma = data.get("dealer_positioning", {}).get("gamma", {})
        dealer = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        return gamma.get("net_gamma", 0) > 0 and dealer.get("dealer_inventory") == "LONG_GAMMA"

    def _no_exit(self, reason):
        return {"exit_plan": None, "position_state": "NO_POSITION", "reason": reason}

    def _full_exit(self, reason):
        return {
            "exit_plan": {"action": "EXIT_FULL", "reason": reason},
            "position_state": "EXIT_NOW",
            "confidence": "HIGH"
        }