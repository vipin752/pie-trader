from typing import Dict, Any, List


class SniperExecutionEngine:

    def calculate(self, ctx: Dict[str, Any]) -> Dict[str, Any]:

        window = ctx.get("trading_window", {}).get("window")
        fake_breakout = ctx.get("fake_breakout", {}).get("is_fake_breakout", False)

        # =========================
        # SAFETY FILTERS
        # =========================
        if window == "OPENING":
            return self._wait("Opening volatility")

        if fake_breakout:
            return self._wait("Fake breakout detected")

        if window != "EXPANSION":
            return self._wait("Waiting for expansion")

        # =========================
        # CONTEXT
        # =========================
        market = ctx.get("market_context", {})
        spot = market.get("spot", 0)

        dealer = ctx.get("dealer_positioning", {}).get("dealer_inventory_model", {})
        gamma_type = dealer.get("dealer_inventory", "")
        net_gamma = dealer.get("net_gamma", 0)

        probability = ctx.get("market_structure", {}).get("probability_model", {})
        direction_bias = probability.get("direction", "")

        liquidity = ctx.get("liquidity_map", {}).get("support_resistance", {})
        support = liquidity.get("support")
        resistance = liquidity.get("resistance")

        strikes = ctx.get("dealer_positioning", {}).get("gamma", {}).get("strikes_enriched", [])

        volume_spikes = ctx.get("institutional_flow", {}).get("volume_spike_engine", {}).get("spikes", [])

        # =========================
        # BREAKOUT VALIDATION (🔥 NEW CORE)
        # =========================
        breakout = None

        if spot > resistance and gamma_type == "SHORT_GAMMA":
            if self._has_volume_confirmation(volume_spikes, resistance):
                breakout = "UP"

        elif spot < support and gamma_type == "SHORT_GAMMA":
            if self._has_volume_confirmation(volume_spikes, support):
                breakout = "DOWN"

        if not breakout:
            return self._wait("Expansion phase - breakout pending")

        # =========================
        # STRIKE SELECTION (🔥 SMART)
        # =========================
        option = self._select_optimal_strike(strikes, spot, breakout)

        if not option:
            return self._wait("No optimal strike")

        strike = option["strike"]
        opt_type = "CE" if breakout == "UP" else "PE"
        ltp = option.get(f"{opt_type.lower()}_ltp", 0)

        if ltp <= 0:
            return self._wait("Invalid premium")

        # =========================
        # TARGET (🔥 GAMMA BASED)
        # =========================
        target = self._dynamic_target(ltp, net_gamma)

        # =========================
        # CONFIDENCE MODEL (🔥 NEW)
        # =========================
        confidence = self._confidence_score(net_gamma, volume_spikes)

        return {
            "strategy": "SNIPER_BUY",
            "direction": breakout,
            "option": f"{strike} {opt_type}",
            "entry": "CONFIRMED_BREAKOUT",
            "entry_price": round(ltp, 2),
            "target": target,
            "confidence": confidence,
            "reason": "VOLUME + GAMMA + STRUCTURE CONFIRMED"
        }

    # =========================================================
    # 🔥 VOLUME CONFIRMATION
    # =========================================================
    def _has_volume_confirmation(self, spikes: List[Dict], level: float) -> bool:

        for s in spikes:
            if abs(s.get("strike", 0) - level) <= 100:
                return True

        return False

    # =========================================================
    # 🎯 SMART STRIKE SELECTION
    # =========================================================
    def _select_optimal_strike(self, strikes: List[Dict], spot: float, direction: str):

        best = None
        best_score = -1

        for s in strikes:
            strike = s.get("strike", 0)

            distance = abs(strike - spot)
            liquidity = s.get("call_volume", 0) + s.get("put_volume", 0)
            oi = s.get("call_oi", 0) + s.get("put_oi", 0)

            if distance > 300:
                continue

            # 🔥 scoring formula (pro level)
            score = (
                (1 / (distance + 1)) * 0.5 +
                (liquidity / 100000) * 0.3 +
                (oi / 100000) * 0.2
            )

            if score > best_score:
                best_score = score
                best = s

        return best

    # =========================================================
    # 🎯 TARGET
    # =========================================================
    def _dynamic_target(self, premium: float, gamma: float) -> int:

        if gamma < 0:
            return int(premium * 2.0)   # aggressive

        return int(premium * 1.5)

    # =========================================================
    # 📊 CONFIDENCE
    # =========================================================
    def _confidence_score(self, gamma: float, spikes: List[Dict]) -> str:

        if gamma < -20 and len(spikes) > 5:
            return "VERY_HIGH"

        if gamma < 0:
            return "HIGH"

        return "MEDIUM"

    # =========================================================
    # WAIT STATE
    # =========================================================
    def _wait(self, reason: str) -> Dict[str, Any]:
        return {
            "strategy": "WAIT",
            "reason": reason,
            "confidence": "HIGH"
        }
        