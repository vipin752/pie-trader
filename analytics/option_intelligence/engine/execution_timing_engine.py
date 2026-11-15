from typing import Dict, Any


class ExecutionTimingEngine:

    def generate(self, data: Dict[str, Any]) -> Dict[str, Any]:

        try:
            # =========================
            # SAFE EXTRACTION
            # =========================
            market_ctx = data.get("market_context", {}) or {}
            session = market_ctx.get("session", {}) or {}

            liquidity = data.get("liquidity_map", {}).get("support_resistance", {}) or {}
            gamma = data.get("dealer_positioning", {}).get("gamma", {}) or {}
            premium = data.get("premium_intelligence", {}) or {}
            flow = data.get("institutional_flow", {}).get("volume_spike_engine", {}) or {}
            execution_debug = data.get("execution_debug", {}) or {}
            compression = data.get("market_structure", {}).get("compression", {}) or {}
            probability = data.get("market_structure", {}).get("probability_model", {}) or {}
            confidence = data.get("confidence", {}) or {}

            auto_trade_wrapper = data.get("auto_trade_decision", {}) or {}
            auto_trade = auto_trade_wrapper.get("auto_trade_decision", auto_trade_wrapper) or {}

            # =========================
            # 🚫 MARKET CLOSED (TOP PRIORITY)
            # =========================
            if session.get("is_market") is False:
                return {
                    "entry_signal": "NO_ENTRY",
                    "entry_type": "NONE",
                    "reason": "Market closed",
                    "confidence": "HIGH"
                }

            # =========================
            # SAFE VALUES
            # =========================
            spot = float(market_ctx.get("spot", 0))

            resistance = liquidity.get("resistance")
            support = liquidity.get("support")

            net_gamma = float(gamma.get("net_gamma", 0))
            spikes = flow.get("spikes", []) or []

            window = execution_debug.get("trading_window", {}).get("window")
            fake_breakout = execution_debug.get("fake_breakout", {}).get("is_fake_breakout", False)

            prob_10x = (
                probability.get("afternoon_session_13_45_14_45", {})
                .get("probabilities", {})
                .get("10x", 0)
            )

            confidence_score = float(confidence.get("confidence_score", 0))

            flip_distance = float(compression.get("flip_distance_pct", 1))
            is_compression = bool(compression.get("compression_detected", False))

            action = auto_trade.get("action", "NO_TRADE")

            # =========================
            # ❌ HARD BLOCKS
            # =========================
            if action == "NO_TRADE":
                return self._wait("Auto trade blocked")

            if fake_breakout:
                return self._wait("Fake breakout detected")

            if window not in ["EXPANSION", "THETA"]:
                return self._wait("Invalid trading window")

            if premium.get("premium_activity") != "HIGH":
                return self._wait("No premium expansion")

            if resistance is None or support is None:
                return self._wait("Invalid support/resistance")

            # =========================
            # 🔥 EXECUTION CONTROL (CRITICAL FIX)
            # =========================
            if action != "EXECUTE":
                # ONLY PREPARE STATES — NO ENTRY
                if is_compression:
                    return {
                        "entry_signal": "PREPARE_BREAKOUT",
                        "entry_type": "COMPRESSION_READY",
                        "reason": "Waiting for breakout confirmation",
                        "confidence": "HIGH"
                    }

                return self._wait("Waiting for execution trigger")

            # =========================
            # CORE CONDITIONS
            # =========================
            breakout_up = spot > resistance
            breakout_down = spot < support

            strong_volume = len(spikes) >= 5

            short_gamma = net_gamma < 0
            long_gamma = net_gamma > 0

            # =========================================================
            # 🚀 1. CONFIRMED BREAKOUT (ONLY EXECUTE MODE)
            # =========================================================
            if short_gamma:

                if breakout_up:
                    if strong_volume and confidence_score >= 70:
                        return self._build_entry(
                            "ENTER_LONG",
                            "BREAKOUT_CONFIRMED",
                            "Short gamma breakout + volume"
                        )
                    return self._wait("Weak breakout")

                if breakout_down:
                    if strong_volume and confidence_score >= 70:
                        return self._build_entry(
                            "ENTER_SHORT",
                            "BREAKDOWN_CONFIRMED",
                            "Short gamma breakdown + volume"
                        )
                    return self._wait("Weak breakdown")

            # =========================================================
            # 🔥 2. EARLY ENTRY (STRICT — NO OVERTRADING)
            # =========================================================
            if short_gamma and is_compression:

                if (
                    flip_distance < 0.10
                    and strong_volume
                    and confidence_score >= 90
                    and prob_10x >= 18
                ):
                    return self._build_entry(
                        "EARLY_ENTRY",
                        "PRE_BREAKOUT",
                        "High probability institutional setup"
                    )

                return {
                    "entry_signal": "PREPARE_BREAKOUT",
                    "entry_type": "COMPRESSION_READY",
                    "reason": "Compression but not strong enough",
                    "confidence": "MEDIUM"
                }

            # =========================================================
            # 🧠 3. RANGE MODE (LONG GAMMA)
            # =========================================================
            if long_gamma:

                near_resistance = abs(spot - resistance) < 20
                near_support = abs(spot - support) < 20

                if near_resistance or near_support:
                    return {
                        "entry_signal": "PREPARE",
                        "entry_type": "RANGE_EDGE",
                        "reason": "Near range boundary",
                        "confidence": "MEDIUM"
                    }

                return {
                    "entry_signal": "PREPARE",
                    "entry_type": "RANGE",
                    "reason": "Mean reversion market",
                    "confidence": "LOW"
                }

            # =========================================================
            # FINAL FALLBACK
            # =========================================================
            return self._wait("No valid execution setup")

        except Exception as e:
            return self._wait(f"Execution error: {str(e)}")

    # =========================================================
    def _build_entry(self, signal: str, entry_type: str, reason: str):

        return {
            "entry_signal": signal,
            "entry_type": entry_type,
            "reason": reason,
            "confidence": "HIGH"
        }

    # =========================================================
    def _wait(self, reason: str):

        return {
            "entry_signal": "WAIT",
            "entry_type": "NO_ENTRY",
            "reason": reason,
            "confidence": "LOW"
        }
        