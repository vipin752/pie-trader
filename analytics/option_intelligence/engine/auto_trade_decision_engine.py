from typing import Dict, Any


class AutoTradeDecisionEngine:

    def generate(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            # =========================
            # CORE DATA
            # =========================
            probability = data.get("market_structure", {}).get("probability_model", {})
            gamma = data.get("dealer_positioning", {}).get("gamma", {})
            dealer = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
            liquidity = data.get("liquidity_map", {}).get("support_resistance", {})
            market = data.get("market_context", {})
            execution_debug = data.get("execution_debug", {})
            compression = data.get("market_structure", {}).get("compression", {})
            premium = data.get("premium_intelligence", {})
            strike_selection = data.get("strike_selection", {})
            confidence = data.get("confidence", {})

            # =========================
            # SAFE VALUES
            # =========================
            spot = market.get("spot", 0)
            strike_gap = market.get("contract", {}).get("strike_gap", 50)

            support = liquidity.get("support")
            resistance = liquidity.get("resistance")

            net_gamma = gamma.get("net_gamma", 0)
            gamma_type = dealer.get("dealer_inventory", "")

            fake_breakout = execution_debug.get("fake_breakout", {}).get("is_fake_breakout", False)

            prob_10x = probability.get("afternoon_session_13_45_14_45", {}) \
                .get("probabilities", {}).get("10x", 0)

            selected_strike = strike_selection.get("selected_strike")

            is_compression = compression.get("compression_detected", False)

            confidence_score = confidence.get("confidence_score", 0)

            # =========================
            # SCORE ENGINE
            # =========================
            score_map = self._evaluate_parameters(data)
            total_score = sum(score_map.values())
            score_level = self._get_score_level(total_score)

            # =========================
            # HARD VALIDATION
            # =========================
            if not selected_strike:
                return self._build_full_output(self._no_trade("No strike"), total_score, score_level, score_map)

            if support is None or resistance is None:
                return self._build_full_output(self._no_trade("Invalid levels"), total_score, score_level, score_map)

            if fake_breakout:
                return self._build_full_output(self._build_fake_breakout_trade(data), total_score, score_level, score_map)

            # =========================
            # DIRECTION
            # =========================
            direction = self._get_direction(data)

            try:
                base_strike, _ = selected_strike.split()
                base_strike = int(base_strike)
            except:
                return self._build_full_output(self._no_trade("Invalid strike format"), total_score, score_level, score_map)

            # =========================
            # 🔥 FIX: ALIGN OPTION TYPE
            # =========================
            option_type = self._get_option_type(direction)
            aligned_option = f"{base_strike} {option_type}"

            # =========================================================
            # 1. BREAKOUT EXECUTION
            # =========================================================
            if net_gamma < 0 and premium.get("premium_activity") == "HIGH":

                if spot > resistance:
                    return self._build_full_output(
                        self._build_trade("UP", max(base_strike, resistance + strike_gap), "CE",
                                          prob_10x, net_gamma, confidence_score),
                        total_score, score_level, score_map
                    )

                if spot < support:
                    return self._build_full_output(
                        self._build_trade("DOWN", min(base_strike, support - strike_gap), "PE",
                                          prob_10x, net_gamma, confidence_score),
                        total_score, score_level, score_map
                    )

            # =========================================================
            # 2. PRE-BREAKOUT (FIXED OPTION)
            # =========================================================
            if net_gamma < 0 and is_compression:

                return self._build_full_output({
                    "strategy": "BREAKOUT_BUILDUP",
                    "action": "PREPARE",
                    "direction": direction,
                    "option": aligned_option,  # ✅ FIXED
                    "confidence": "HIGH" if confidence_score >= 70 else "MEDIUM",
                    "reason": "Short gamma + compression"
                }, total_score, score_level, score_map)

            # =========================================================
            # 3. RANGE
            # =========================================================
            if gamma_type == "LONG_GAMMA":

                return self._build_full_output({
                    "strategy": "RANGE",
                    "action": "PREPARE",
                    "direction": "MEAN_REVERSION",
                    "buy_zone": support,
                    "sell_zone": resistance,
                    "option": aligned_option,
                    "confidence": "MODERATE",
                    "reason": "Long gamma range"
                }, total_score, score_level, score_map)

            # =========================================================
            # 4. TREND FOLLOW
            # =========================================================
            if net_gamma < 0 and confidence_score >= 75:

                return self._build_full_output({
                    "strategy": "TREND_FOLLOW",
                    "action": "PREPARE",
                    "direction": direction,
                    "option": aligned_option,
                    "confidence": "HIGH",
                    "reason": "Trend continuation"
                }, total_score, score_level, score_map)

            # =========================================================
            # FALLBACK
            # =========================================================
            if total_score >= 5:
                return self._build_full_output({
                    "strategy": "PREPARE",
                    "action": "PREPARE",
                    "direction": direction,
                    "option": aligned_option,
                    "confidence": "MEDIUM",
                    "reason": "Moderate setup"
                }, total_score, score_level, score_map)

            return self._build_full_output(self._no_trade("No strong setup"), total_score, score_level, score_map)

        except Exception as e:
            return self._no_trade(f"Error: {str(e)}")

    # =========================================================
    # ✅ OPTION ALIGNMENT (CORE FIX)
    # =========================================================
    def _get_option_type(self, direction: str) -> str:
        if direction == "UP_BIAS":
            return "CE"
        if direction == "DOWN_BIAS":
            return "PE"
        return "CE"

    # =========================================================
    def _get_direction(self, data):

        flow = data.get("institutional_flow", {}).get("smart_money_flow_engine", {})
        atm_flow = flow.get("atm_flow")

        if atm_flow == "ATM_PUT_BUYING":
            return "DOWN_BIAS"
        elif atm_flow == "ATM_CALL_BUYING":
            return "UP_BIAS"
        return "NEUTRAL"

    # =========================================================
    def _evaluate_parameters(self, data):

        score = {}

        gamma = data.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        execution = data.get("execution_debug", {})
        premium = data.get("premium_intelligence", {})
        compression = data.get("market_structure", {}).get("compression", {})
        probability = data.get("market_structure", {}).get("probability_model", {})
        confidence = data.get("confidence", {})

        score["gamma"] = 1 if gamma.get("dealer_inventory") == "SHORT_GAMMA" else 0
        score["window"] = 1 if execution.get("trading_window", {}).get("window") == "EXPANSION" else 0
        score["premium"] = 1 if premium.get("premium_activity") == "HIGH" else 0
        score["volume"] = 1 if len(data.get("institutional_flow", {}).get("volume_spike_engine", {}).get("spikes", [])) >= 5 else 0
        score["compression"] = 1 if compression.get("compression_detected") else 0
        score["probability"] = 1 if probability.get("afternoon_session_13_45_14_45", {}).get("probabilities", {}).get("10x", 0) >= 15 else 0
        score["confidence"] = 1 if confidence.get("confidence_score", 0) >= 70 else 0

        return score

    def _get_score_level(self, score):
        if score >= 7:
            return "HIGH"
        elif score >= 5:
            return "MODERATE"
        elif score >= 3:
            return "LOW"
        return "AVOID"

    # =========================================================
    def _build_full_output(self, trade, score, level, score_map):

        return {
            "auto_trade_decision": trade,
            "trade_recommendations": {
                "score": score,
                "confidence_level": level,
                "parameter_breakdown": score_map,
                "safe_trade": {"action": "WAIT", "confidence": "HIGH"},
                "moderate_trade": {"action": "PREPARE", "confidence": "MEDIUM"},
                "aggressive_trade": {"action": "EXECUTE", "confidence": "LOW"}
            }
        }

    # =========================================================
    def _build_trade(self, direction, strike, option_type, prob, gamma, confidence_score):

        return {
            "strategy": "SNIPER_BUY",
            "action": "EXECUTE",
            "direction": direction,
            "option": f"{strike} {option_type}",
            "entry_type": "BREAKOUT_CONFIRMATION",
            "probability": prob,
            "confidence": "HIGH" if prob > 20 else "MEDIUM",
            "confidence_score": confidence_score,
            "target_multiplier": 1.8 if gamma < 0 else 1.5,
            "reason": "Breakout + short gamma"
        }

    # =========================================================
    def _build_fake_breakout_trade(self, data):

        return {
            "strategy": "FAKE_BREAKOUT",
            "action": "EXECUTE",
            "direction": "REVERSAL",
            "confidence": "HIGH",
            "reason": "Trap detected"
        }

    # =========================================================
    def _no_trade(self, reason: str):
        return {
            "strategy": "WAIT",
            "action": "NO_TRADE",
            "reason": reason,
            "confidence": "HIGH"
        }
        