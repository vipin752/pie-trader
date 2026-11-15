from typing import Dict, Any


class RiskManagementEngine:

    def __init__(self, capital: float = 100000):
        self.capital = capital

    # =========================================================
    def apply(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            # =========================
            # SAFE EXTRACTION
            # =========================
            auto_trade_wrapper = data.get("auto_trade_decision", {}) or {}
            auto_trade = auto_trade_wrapper.get("auto_trade_decision", auto_trade_wrapper) or {}

            execution = data.get("execution_timing", {}) or {}
            strike = data.get("strike_selection", {}) or {}
            market = data.get("market_context", {}) or {}
            premium = data.get("premium_intelligence", {}) or {}

            action = auto_trade.get("action", "NO_TRADE")
            direction = auto_trade.get("direction")

            entry_signal = execution.get("entry_signal")

            # =========================
            # 🚫 MARKET CLOSED PROTECTION (STRICT)
            # =========================
            session = market.get("session", {}) or {}
            is_market_open = session.get("is_market")

            if is_market_open is False:
                return self._no_trade("Market closed")

            # =========================
            # 🔥 EXECUTION VALIDATION (STRICT)
            # =========================
            is_early_entry = entry_signal == "EARLY_ENTRY"
            is_execute = action == "EXECUTE"

            if not is_execute and not is_early_entry:
                return self._no_trade("Trade not executable")

            # =========================
            # 🔥 ALIGN STRIKE WITH DIRECTION
            # =========================
            selected = self._align_option_with_direction(data)

            if not selected or " " not in selected:
                return self._no_trade("Invalid strike format")

            try:
                strike_price, option_type = selected.split()
                strike_price = float(strike_price)
            except Exception:
                return self._no_trade("Strike parsing failed")

            # =========================
            # PREMIUM FETCH
            # =========================
            entry_price = self._get_ltp(premium, strike_price, option_type)

            if not entry_price or entry_price <= 0:
                return self._no_trade("Invalid premium")

            # =========================
            # DYNAMIC RISK
            # =========================
            risk_amount = self._dynamic_risk(data)

            if risk_amount <= 0:
                return self._no_trade("Invalid risk amount")

            # =========================
            # DYNAMIC SL
            # =========================
            sl_pct = self._dynamic_sl(data)

            if sl_pct <= 0 or sl_pct >= 1:
                return self._no_trade("Invalid SL %")

            stop_loss = entry_price * (1 - sl_pct)

            risk_per_lot = entry_price - stop_loss

            if risk_per_lot <= 0:
                return self._no_trade("Invalid SL calculation")

            # =========================
            # LOT CALCULATION
            # =========================
            contract = market.get("contract", {}) or {}
            lot_size = contract.get("lot_size", 50)

            denominator = risk_per_lot * lot_size

            if denominator <= 0:
                return self._no_trade("Invalid lot calculation")

            lots = int(risk_amount / denominator)

            if lots < 1:
                return self._no_trade("Capital too small")

            # =========================
            # TARGETS
            # =========================
            targets = self._build_targets(entry_price)

            # =========================
            # POSITION MODE
            # =========================
            position_mode = "FULL_ENTRY"
            if is_early_entry:
                position_mode = "PARTIAL_ENTRY"

            return {
                "capital": self.capital,
                "risk_amount": round(risk_amount, 2),

                "entry_price": round(entry_price, 2),
                "stop_loss": round(stop_loss, 2),

                "sl_pct": round(sl_pct * 100, 2),

                "lots": lots,
                "quantity": lots * lot_size,

                "targets": targets,

                "position_mode": position_mode,
                "risk_model": "DYNAMIC_EXECUTION_AWARE",
                "position_type": f"OPTION_BUY_{option_type}",

                "selected_strike": selected,

                "advice": self._advice(data, sl_pct),
                "confidence": "HIGH"
            }

        except Exception as e:
            return self._no_trade(f"Risk error: {str(e)}")

    # =========================================================
    # ALIGN OPTION WITH DIRECTION (STRICT)
    # =========================================================
    def _align_option_with_direction(self, data):

        auto_trade = data.get("auto_trade_decision", {}).get("auto_trade_decision", {}) or {}
        strike = data.get("strike_selection", {}) or {}

        direction = auto_trade.get("direction")
        selected = strike.get("selected_strike")

        if not selected or " " not in selected:
            return selected

        try:
            strike_price, option_type = selected.split()

            if direction == "DOWN_BIAS":
                return f"{strike_price} PE"

            if direction == "UP_BIAS":
                return f"{strike_price} CE"

            return selected

        except Exception:
            return selected

    # =========================================================
    def _dynamic_risk(self, data):

        confidence = data.get("confidence", {}).get("confidence_score", 50)
        entry_signal = data.get("execution_timing", {}).get("entry_signal")

        probability = (
            data.get("market_structure", {})
            .get("probability_model", {})
            .get("afternoon_session_13_45_14_45", {})
            .get("probabilities", {})
            .get("10x", 0)
        )

        if entry_signal == "EARLY_ENTRY":
            return self.capital * 0.01

        if confidence >= 80 and probability >= 18:
            return self.capital * 0.03

        if confidence >= 70:
            return self.capital * 0.02

        return self.capital * 0.01

    # =========================================================
    def _dynamic_sl(self, data):

        gamma = data.get("dealer_positioning", {}).get("gamma", {}) or {}
        net_gamma = gamma.get("net_gamma", 0)

        compression = data.get("market_structure", {}).get("compression", {}) or {}
        flip_distance = compression.get("flip_distance_pct", 1)
        is_compression = compression.get("compression_detected")

        entry_type = data.get("execution_timing", {}).get("entry_type")

        if is_compression and flip_distance < 0.05:
            return 0.20

        if entry_type == "PRE_BREAKOUT":
            return 0.30

        if net_gamma < 0:
            return 0.25

        return 0.35

    # =========================================================
    def _get_ltp(self, premium, strike_price, option_type):

        for s in premium.get("top_strikes", []) or []:
            if s.get("strike") == strike_price:
                return s.get("call_ltp") if option_type == "CE" else s.get("put_ltp")

        return 0

    # =========================================================
    def _build_targets(self, entry):

        return {
            "t1": round(entry * 1.5, 2),
            "t2": round(entry * 2.5, 2),
            "t3": round(entry * 5, 2),
            "runner": "OPEN (10x+)"
        }

    # =========================================================
    def _advice(self, data, sl_pct):

        entry_signal = data.get("execution_timing", {}).get("entry_signal")

        return {
            "rule_1": "Exit immediately on SL hit",
            "rule_2": "Book 50% at 1.5x",
            "rule_3": "Trail SL after T2",
            "rule_4": f"SL fixed at {int(sl_pct * 100)}%",
            "rule_5": f"Entry type: {entry_signal}",
            "rule_6": "Max 2 trades per day",
            "rule_7": "No revenge trading"
        }

    # =========================================================
    def _no_trade(self, reason):

        return {
            "position": "NO_TRADE",
            "reason": reason,
            "confidence": "HIGH"
        }
        