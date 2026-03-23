"""
PIE TRADER — option_intelligence/scoring/intelligence_score.py

10-Component Intelligence Scoring Engine (100 points max).

Document formula:
  Structure   : 0–10   (BOS, CHoCH, trend alignment)
  Liquidity   : 0–15   (sweep, equal levels, inducement)
  Order Block : 0–15   (fresh OB, OB+sweep, mitigation)
  FVG         : 0–10   (FVG entry, FVG+OB confluence)
  Gamma       : 0–15   (short gamma, near flip, wall break)
  Delta       : 0–10   (delta confirms direction)
  Volatility  : 0–10   (compression, expansion, IV)
  Timeframe   : 0–10   (HTF alignment)
  Time        : 0–5    (killzone, session)
  Risk/Reward : 0–10   (RR ≥ 1:2 = +7, ≥ 1:3 = +10)
  ─────────────────────
  Total       : 0–100+

Score → Action Mapping (document section 3):
  < 40   NO_TRADE
  40–54  WAIT
  55–69  PREPARE
  70–84  READY
  ≥ 85   EXECUTE

After EXECUTE confirmation breakout (+20 bonus from Java):
  PREPARE(67) + breakout_bonus(20) = 87 → EXECUTE

Output:
  {
    "total_score":     int,
    "state":           "NO_TRADE" | "WAIT" | "PREPARE" | "READY" | "EXECUTE",
    "action":          str,
    "setup":           str,        # e.g. "LIQUIDITY_SWEEP + OB + FVG"
    "direction":       str,        # "UP" | "DOWN" | "NEUTRAL"
    "score_breakdown": { component: score, ... }
  }
"""

from typing import Dict, Any


class IntelligenceScore:

    def calculate(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Args:
            data: Full final_output from option_engine.analyze()
                  Must contain: smc_analysis, delta_analysis, vwap_analysis,
                  trap_analysis, timeframe_analysis, dealer_positioning,
                  market_structure, volatility_context, execution_layer,
                  risk_management, execution_debug
        """
        try:
            breakdown: Dict[str, int] = {}

            # ── (A) STRUCTURE SCORE (0-10) ────────────────────────────────
            breakdown["structure"]   = self._structure_score(data)
            # ── (B) LIQUIDITY SCORE (0-15) ────────────────────────────────
            breakdown["liquidity"]   = self._liquidity_score(data)
            # ── (C) ORDER BLOCK SCORE (0-15) ──────────────────────────────
            breakdown["order_block"] = self._ob_score(data)
            # ── (D) FVG SCORE (0-10) ──────────────────────────────────────
            breakdown["fvg"]         = self._fvg_score(data)
            # ── (E) GAMMA / OPTIONS SCORE (0-15) ──────────────────────────
            breakdown["gamma"]       = self._gamma_score(data)
            # ── (F) DELTA SCORE (0-10) ────────────────────────────────────
            breakdown["delta"]       = self._delta_score(data)
            # ── (G) VOLATILITY SCORE (0-10) ───────────────────────────────
            breakdown["volatility"]  = self._volatility_score(data)
            # ── (H) TIMEFRAME SCORE (0-10) ────────────────────────────────
            breakdown["timeframe"]   = self._timeframe_score(data)
            # ── (I) TIME / KILLZONE SCORE (0-5) ───────────────────────────
            breakdown["time"]        = self._time_score(data)
            # ── (J) RISK/REWARD SCORE (0-10) ──────────────────────────────
            breakdown["rr"]          = self._rr_score(data)

            total = sum(breakdown.values())
            total = max(0, total)   # floor at 0 (penalties can drag below)

            state, action = self._state_action(total)
            setup         = self._detect_setup(data, breakdown)
            direction     = self._direction(data)

            return {
                "total_score":     total,
                "state":           state,
                "action":          action,
                "setup":           setup,
                "direction":       direction,
                "score_breakdown": breakdown,
            }

        except Exception as e:
            return {
                "total_score": 0, "state": "NO_TRADE", "action": "NO_TRADE",
                "setup": "NONE", "direction": "NEUTRAL",
                "score_breakdown": {}, "error": str(e),
            }

    # ─────────────────────────────────────────────────────────────────────────
    # A. STRUCTURE (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _structure_score(self, data: Dict) -> int:
        smc = data.get("smc_analysis", {}).get("smc", {})
        ms  = smc.get("market_structure", {})
        score = 0
        if ms.get("bos"):    score += 5   # break of structure
        if ms.get("choch"):  score += 5   # change of character
        if ms.get("structure") in ("HH_HL", "LL_LH"): score += 5
        if ms.get("mss"):    score += 2
        # Against structure = penalty
        tf_dir = (data.get("timeframe_analysis", {})
                      .get("alignment", {})
                      .get("direction_filter", "NEUTRAL"))
        smc_bias = data.get("smc_analysis", {}).get("bias", "NEUTRAL")
        if tf_dir != "NEUTRAL" and smc_bias != "NEUTRAL":
            if (tf_dir == "UP" and smc_bias == "BEARISH") or \
               (tf_dir == "DOWN" and smc_bias == "BULLISH"):
                score -= 5   # against higher TF structure
        return max(0, min(10, score))

    # ─────────────────────────────────────────────────────────────────────────
    # B. LIQUIDITY (0-15)
    # ─────────────────────────────────────────────────────────────────────────
    def _liquidity_score(self, data: Dict) -> int:
        smc = data.get("smc_analysis", {}).get("smc", {})
        liq = smc.get("liquidity", {})
        sweep = liq.get("sweep", "NONE")
        score = 0
        if sweep in ("SELL_SIDE_SWEEP", "BUY_SIDE_SWEEP"): score += 10
        elif sweep in ("BELOW_SUPPORT", "ABOVE_RESISTANCE"):  score += 5
        if liq.get("equal_highs") or liq.get("equal_lows"):  score += 3
        if liq.get("inducement"):                              score += 2
        return max(0, min(15, score))

    # ─────────────────────────────────────────────────────────────────────────
    # C. ORDER BLOCK (0-15)
    # ─────────────────────────────────────────────────────────────────────────
    def _ob_score(self, data: Dict) -> int:
        smc   = data.get("smc_analysis", {}).get("smc", {})
        ob    = smc.get("order_block", {})
        sweep = smc.get("liquidity", {}).get("sweep", "NONE")
        bias  = data.get("smc_analysis", {}).get("bias", "NEUTRAL")
        score = 0

        # Select relevant OB based on bias
        relevant_ob = ob.get("bearish_ob") if bias == "BEARISH" else ob.get("bullish_ob")
        if relevant_ob:
            ob_type = relevant_ob.get("type", "")
            if ob_type == "FRESH":       score += 10  # untouched OB = max value
            elif ob_type == "MITIGATION": score += 5  # retesting OB = good entry
            elif ob_type == "BREAKER":   score -= 5   # broken OB = avoid
            # OB + sweep = highest probability combo
            if ob_type in ("FRESH", "MITIGATION") and sweep != "NONE":
                score += 5
        return max(0, min(15, score))

    # ─────────────────────────────────────────────────────────────────────────
    # D. FAIR VALUE GAP (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _fvg_score(self, data: Dict) -> int:
        smc   = data.get("smc_analysis", {}).get("smc", {})
        fvg   = smc.get("fvg", {})
        ob    = smc.get("order_block", {})
        bias  = data.get("smc_analysis", {}).get("bias", "NEUTRAL")
        score = 0

        nearest = fvg.get("nearest_fvg")
        filled  = fvg.get("filled", True)

        if not filled:
            if (bias == "BEARISH" and nearest == "BEARISH") or \
               (bias == "BULLISH" and nearest == "BULLISH"):
                score += 5
            # FVG + OB confluence = +5 bonus
            relevant_ob = ob.get("bearish_ob") if bias == "BEARISH" else ob.get("bullish_ob")
            if relevant_ob and relevant_ob.get("mitigated") and not filled:
                score += 5
        return max(0, min(10, score))

    # ─────────────────────────────────────────────────────────────────────────
    # E. GAMMA / OPTIONS (0-15)
    # ─────────────────────────────────────────────────────────────────────────
    def _gamma_score(self, data: Dict) -> int:
        dealer = (data.get("dealer_positioning", {})
                      .get("dealer_inventory_model", {}))
        gamma  = data.get("dealer_positioning", {}).get("gamma", {})
        inv    = dealer.get("dealer_inventory", "")
        gf     = dealer.get("gamma_flip", 0) or 0
        spot   = (data.get("market_context", {}) or {}).get("spot", 0) or 0
        liq    = (data.get("liquidity_map", {}) or {}).get("support_resistance", {}) or {}
        support    = liq.get("support", 0) or 0
        resistance = liq.get("resistance", 0) or 0
        score = 0

        if inv == "SHORT_GAMMA":     score += 10  # dealers short = market will trend
        elif inv == "LONG_GAMMA":    score -= 5   # dealers long = market will range
        if gf and spot:
            dist_pct = abs(spot - gf) / spot * 100
            if dist_pct < 0.5:       score += 5  # near gamma flip = trigger
        if spot and resistance and spot > resistance: score += 5  # call wall break
        if spot and support    and spot < support:    score += 5  # put wall break
        return max(0, min(15, score))

    # ─────────────────────────────────────────────────────────────────────────
    # F. DELTA (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _delta_score(self, data: Dict) -> int:
        delta = data.get("delta_analysis", {})
        smc   = data.get("smc_analysis", {})
        db    = delta.get("delta_bias", "NEUTRAL")
        conf  = delta.get("confidence", "LOW")
        bias  = smc.get("bias", "NEUTRAL")
        score = 0

        # Delta confirms SMC direction = +10
        if (db == "BEARISH" and bias == "BEARISH") or \
           (db == "BULLISH" and bias == "BULLISH"):
            score += 10 if conf == "HIGH" else 7
        elif db != "NEUTRAL" and bias != "NEUTRAL" and db != bias[:4].upper():
            score -= 5   # divergence = penalty
        return max(-5, min(10, score))

    # ─────────────────────────────────────────────────────────────────────────
    # G. VOLATILITY (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _volatility_score(self, data: Dict) -> int:
        compression = (data.get("market_structure", {})
                           .get("compression", {}))
        vol_ctx = (data.get("volatility_context", {})
                       .get("volatility_engine", {}))
        trap    = data.get("trap_analysis", {})
        dealer  = (data.get("dealer_positioning", {})
                       .get("dealer_inventory_model", {}))
        score = 0

        if compression.get("compression_detected"): score += 5  # breakout building
        vol_regime = dealer.get("volatility_regime", "")
        if "EXPANDING" in vol_regime:                score += 5  # move started
        atm_iv = float(vol_ctx.get("atm_iv_pct", 15) or 15)
        if atm_iv > 50:                              score -= 5  # IV too high
        if trap.get("trap_type") == "IV_SPIKE_TRAP": score -= 10 # IV crush risk
        return max(-10, min(10, score))

    # ─────────────────────────────────────────────────────────────────────────
    # H. TIMEFRAME ALIGNMENT (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _timeframe_score(self, data: Dict) -> int:
        tf    = data.get("timeframe_analysis", {})
        align = tf.get("alignment", {})
        score = 0
        # Document: weekly+daily = +5, daily+intraday = +5, against HTF = -5
        if align.get("weekly_daily"):    score += 5
        if align.get("daily_intraday"):  score += 5
        if not align.get("monthly_weekly") and align.get("all_aligned") is False:
            score -= 5
        return max(-5, min(10, score))

    # ─────────────────────────────────────────────────────────────────────────
    # I. TIME / KILLZONE (0-5)
    # ─────────────────────────────────────────────────────────────────────────
    def _time_score(self, data: Dict) -> int:
        smc = data.get("smc_analysis", {}).get("smc", {})
        tm  = smc.get("time_model", {})
        kz  = tm.get("killzone", "NORMAL")
        score = 0
        if kz in ("NY_OPEN", "POWER_HOUR"): score += 5
        elif kz == "LUNCH_AVOID":            score -= 5
        elif kz == "INDIA_OPEN":             score += 3
        return max(-5, min(5, score))

    # ─────────────────────────────────────────────────────────────────────────
    # J. RISK/REWARD (0-10)
    # ─────────────────────────────────────────────────────────────────────────
    def _rr_score(self, data: Dict) -> int:
        risk = data.get("risk_management", {})
        targets = risk.get("targets", {})
        entry   = risk.get("entry_price", 0) or 0
        sl      = risk.get("stop_loss", 0)   or 0
        # Try to compute RR from targets
        t1 = targets.get("target_1") if isinstance(targets, dict) else 0
        rr = 0.0
        if entry and sl and t1:
            risk_pts   = abs(entry - sl)
            reward_pts = abs(t1 - entry)
            rr = reward_pts / risk_pts if risk_pts > 0 else 0
        if rr >= 3:    return 10
        if rr >= 2:    return 7
        if rr >= 1:    return 3
        if rr > 0:     return 0
        return 5       # default moderate score when RR not yet computable

    # ─────────────────────────────────────────────────────────────────────────
    # STATE / ACTION MAPPING (document section 3)
    # ─────────────────────────────────────────────────────────────────────────
    def _state_action(self, score: int):
        if   score >= 85: return "EXECUTE",  "EXECUTE"
        elif score >= 70: return "READY",    "READY"
        elif score >= 55: return "PREPARE",  "PREPARE"
        elif score >= 40: return "WAIT",     "WAIT"
        else:             return "NO_TRADE", "NO_TRADE"

    # ─────────────────────────────────────────────────────────────────────────
    # SETUP DETECTION (document "most powerful setups")
    # ─────────────────────────────────────────────────────────────────────────
    def _detect_setup(self, data: Dict, bd: Dict) -> str:
        setups = []
        # From document: highest probability combos
        smc  = data.get("smc_analysis", {})
        sweep = smc.get("liquidity_sweep", "NONE")
        ob_s  = bd.get("order_block", 0)
        fvg_s = bd.get("fvg", 0)
        gma_s = bd.get("gamma", 0)
        bos   = (smc.get("smc") or {}).get("market_structure", {}).get("bos", False)
        comp  = (data.get("market_structure") or {}).get("compression", {}).get("compression_detected", False)

        if sweep != "NONE" and ob_s >= 10:        setups.append("LIQUIDITY_SWEEP+OB")
        if bos and fvg_s >= 5:                    setups.append("BOS+FVG")
        if gma_s >= 10 and comp:                  setups.append("SHORT_GAMMA+BREAKOUT")
        if comp and sweep != "NONE":              setups.append("COMPRESSION+SWEEP")
        if not setups:
            if ob_s >= 10:  setups.append("OB")
            if fvg_s >= 5:  setups.append("FVG")
            if gma_s >= 10: setups.append("SHORT_GAMMA")

        return " + ".join(setups) if setups else "NONE"

    # ─────────────────────────────────────────────────────────────────────────
    # DIRECTION
    # ─────────────────────────────────────────────────────────────────────────
    def _direction(self, data: Dict) -> str:
        # Hierarchy: SMC bias → delta → timeframe filter
        smc_bias = data.get("smc_analysis", {}).get("bias", "NEUTRAL")
        delta_b  = data.get("delta_analysis", {}).get("delta_bias", "NEUTRAL")
        tf_dir   = (data.get("timeframe_analysis", {})
                        .get("alignment", {})
                        .get("direction_filter", "NEUTRAL"))

        if smc_bias == "BEARISH":  return "DOWN"
        if smc_bias == "BULLISH":  return "UP"
        if delta_b  == "BEARISH":  return "DOWN"
        if delta_b  == "BULLISH":  return "UP"
        if tf_dir   == "DOWN":     return "DOWN"
        if tf_dir   == "UP":       return "UP"
        return "NEUTRAL"
    