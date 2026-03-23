from typing import Dict, Any


class PositionManagementEngine:

    def manage(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            auto_trade_wrapper = data.get("auto_trade_decision", {})
            auto_trade = auto_trade_wrapper.get("auto_trade_decision", auto_trade_wrapper)

            execution = data.get("execution_timing", {})
            risk = data.get("risk_management", {})

            action = auto_trade.get("action", "NO_TRADE")
            entry_signal = execution.get("entry_signal", "WAIT")
            position_mode = risk.get("position_mode", "NONE")

            entry = risk.get("entry_price")
            sl = risk.get("stop_loss")
            targets = risk.get("targets") or {}

            # =========================
            # NO TRADE
            # =========================
            if action == "NO_TRADE":
                return {
                    "position_status": "WAITING",
                    "state": "NO_SETUP",
                    "reason": auto_trade.get("reason", "No trade"),
                    "confidence": "HIGH"
                }

            # =========================
            # PREPARE
            # =========================
            if action == "PREPARE" and entry_signal != "EARLY_ENTRY":
                return {
                    "position_status": "WAITING",
                    "state": "PREPARING",
                    "plan": {
                        "instruction": "Wait for breakout confirmation",
                        "next_trigger": "ENTER_LONG / ENTER_SHORT"
                    },
                    "confidence": "MEDIUM"
                }

            # =========================
            # PILOT ENTRY
            # =========================
            if entry_signal == "EARLY_ENTRY" or position_mode in ["PARTIAL_ENTRY", "PILOT"]:

                if not entry or not sl:
                    return self._no_position("No active trade (risk blocked)")

                return {
                    "position_status": "ACTIVE",
                    "state": "PILOT_ENTRY",
                    "entry_type": "EARLY_ENTRY",
                    "position_size": "PARTIAL",

                    "entry": entry,
                    "stop_loss": sl,

                    "plan": {
                        "phase": "INITIAL_PROBE",
                        "instruction": "Small position before breakout",
                        "next_action": "Add position on breakout confirmation"
                    },

                    "scaling_plan": {
                        "add_on_breakout": True,
                        "condition": "Break structure with volume",
                        "action": "Convert to FULL position"
                    },

                    "management_plan": self._management_plan(targets, sl, mode="pilot"),
                    "discipline_rules": self._rules(),

                    "confidence": "HIGH"
                }

            # =========================
            # FULL ENTRY
            # =========================
            if entry_signal in ["ENTER_LONG", "ENTER_SHORT"]:

                if not entry or not sl:
                    return self._no_position("Invalid risk data")

                return {
                    "position_status": "ACTIVE",
                    "state": "FULL_ENTRY",

                    "entry_type": entry_signal,
                    "position_size": "FULL",

                    "entry": entry,
                    "stop_loss": sl,

                    "management_plan": self._management_plan(targets, sl, mode="full"),
                    "discipline_rules": self._rules(),

                    "confidence": "HIGH"
                }

            return self._no_position("No valid position state")

        except Exception as e:
            return self._no_position(f"Error: {str(e)}")

    def _management_plan(self, targets, sl, mode="full"):

        if mode == "pilot":
            return {
                "stage_1": {
                    "trigger": f"price >= {targets.get('t1')}",
                    "action": ["Book 70%", "Move SL to cost"]
                },
                "stage_2": {
                    "trigger": f"price >= {targets.get('t2')}",
                    "action": ["Hold remaining", "Trail aggressively"]
                },
                "stop_loss_rule": {
                    "condition": f"price <= {sl}",
                    "action": ["Exit immediately"]
                }
            }

        return {
            "stage_1": {
                "trigger": f"price >= {targets.get('t1')}",
                "action": ["Book 50%", "Move SL to cost"]
            },
            "stage_2": {
                "trigger": f"price >= {targets.get('t2')}",
                "action": ["Book 30%", "Trail SL to T1"]
            },
            "stage_3": {
                "trigger": f"price >= {targets.get('t3')}",
                "action": ["Hold 20%", "Aggressive trailing SL"]
            },
            "stop_loss_rule": {
                "condition": f"price <= {sl}",
                "action": ["Exit immediately"]
            }
        }

    def _rules(self):
        return [
            "Do not exit before T1",
            "Do not widen stop loss",
            "Follow system strictly",
            "Max 2 trades per day",
            "After 2 SL → STOP trading"
        ]

    def _no_position(self, reason):
        return {
            "position_status": "NO_POSITION",
            "reason": reason,
            "confidence": "HIGH"
        }
        