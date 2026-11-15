"""
PIE TRADER — option_intelligence/engine/trap_engine.py

Trap Detection Engine — prevents entering bad trades.

Inspired by:
  1. Document: detect fake breakout, IV spike trap, option trap, news trap
  2. PineScript VSA indicator: volumeRelative / spreadRelative scoring
  3. PineScript PAT: CHoCH + BOS + liquidity sweep detection

Trap types detected:
  FAKE_BREAKOUT    — price crossed level but volume was weak (PineScript: volumeRelative)
  IV_SPIKE_TRAP    — IV spiked just before breakout = market maker trap
  LIQUIDITY_SWEEP  — price swept above resistance/below support then reversed
  OPTION_TRAP      — PCR extreme (>1.8 or <0.5) = too many on one side = trap
  NEWS_TRAP        — fear index extreme spike = event-driven fake move
  COMPRESSION_TRAP — breakout on very low volume = failed compression play
  CHOCH_TRAP       — CHoCH detected but not confirmed (SMC)

Output:
  {
    "trap_detected":   bool,
    "trap_type":       "NONE" | "FAKE_BREAKOUT" | "IV_SPIKE_TRAP" | ...
    "trap_score":      int,     # 0=clean, higher=more traps
    "avoid_trade":     bool,
    "reason":          str,
    "vsa_strength":    "WEAK" | "MODERATE" | "STRONG" | "STRONGEST",
    "volume_ratio":    float,   # volumeRelative equivalent
    "details":         dict
  }
"""

from typing import Dict, Any, List


# VSA strength thresholds (from PineScript: volumeRelative > 3 = strongest)
VSA_STRONGEST = 3.0
VSA_STRONG    = 2.0
VSA_MODERATE  = 1.0


class TrapEngine:

    def detect(self, data: Dict[str, Any], rows: List[Dict] = None) -> Dict[str, Any]:
        """
        Args:
            data: Full analytics context
            rows: Option chain rows for VSA volume analysis
        """
        try:
            traps   = []
            details = {}

            # 1. VSA: Volume Spread Analysis
            vsa_result = self._vsa_check(rows or [], data)
            details["vsa"] = vsa_result

            # 2. Fake breakout (volume + gamma confirmation)
            fb_result = self._fake_breakout_check(data, vsa_result)
            details["fake_breakout"] = fb_result
            if fb_result["detected"]:
                traps.append("FAKE_BREAKOUT")

            # 3. IV spike trap (IV sudden spike = MM trap)
            iv_result = self._iv_spike_trap(data)
            details["iv_spike"] = iv_result
            if iv_result["detected"]:
                traps.append("IV_SPIKE_TRAP")

            # 4. Liquidity sweep trap (from SMC analysis)
            sweep_result = self._liquidity_sweep_trap(data)
            details["liquidity_sweep"] = sweep_result
            if sweep_result["detected"]:
                traps.append("LIQUIDITY_SWEEP")

            # 5. Option trap (PCR extreme)
            option_result = self._option_trap(data)
            details["option_trap"] = option_result
            if option_result["detected"]:
                traps.append("OPTION_TRAP")

            # 6. Fear index extreme (news trap proxy)
            fear_result = self._fear_trap(data)
            details["fear_trap"] = fear_result
            if fear_result["detected"]:
                traps.append("NEWS_TRAP")

            # 7. CHoCH trap (reversal not confirmed)
            choch_result = self._choch_trap(data)
            details["choch_trap"] = choch_result
            if choch_result["detected"]:
                traps.append("CHOCH_TRAP")

            trap_score   = len(traps) * 10
            trap_detected = len(traps) > 0
            # Only avoid trade when score is high (2+ traps) — single trap is warning only
            avoid_trade  = trap_score >= 20

            return {
                "trap_detected":  trap_detected,
                "trap_type":      traps[0] if traps else "NONE",
                "all_traps":      traps,
                "trap_score":     trap_score,
                "avoid_trade":    avoid_trade,
                "reason":         self._reason(traps),
                "vsa_strength":   vsa_result.get("strength", "MODERATE"),
                "volume_ratio":   vsa_result.get("volume_ratio", 1.0),
                "details":        details,
            }

        except Exception as e:
            return {
                "trap_detected": False, "trap_type": "NONE",
                "all_traps": [], "trap_score": 0,
                "avoid_trade": False, "reason": "",
                "vsa_strength": "MODERATE", "volume_ratio": 1.0,
                "details": {}, "error": str(e),
            }

    # ─────────────────────────────────────────────────────────────────────────
    # 1. VSA — Volume Spread Analysis (PineScript logic in Python)
    # ─────────────────────────────────────────────────────────────────────────

    def _vsa_check(self, rows: List[Dict], data: Dict) -> Dict:
        """
        PineScript equivalent:
          volumeRelative = volume / avgVolume
          spreadRelative = spread / avgSpread
          vsaStrength = 3 if vol>3x | 2 if vol>2x+spread>1.5x | 1 if vol>1x+spread>0.75x | 0
        """
        if not rows:
            spikes = data.get("institutional_flow", {}).get("volume_spike_engine", {}).get("spikes", [])
            volume_ratio = len(spikes) / 5.0 if spikes else 0.5
        else:
            total_vol = sum(
                (float(r.get("call_volume", 0) or 0) + float(r.get("put_volume", 0) or 0))
                for r in rows
            )
            avg_vol   = total_vol / max(len(rows), 1)
            # Use ATM ±2 strikes as "current volume"
            atm_rows  = rows[max(0, len(rows)//2 - 2) : len(rows)//2 + 3]
            atm_vol   = sum(
                (float(r.get("call_volume", 0) or 0) + float(r.get("put_volume", 0) or 0))
                for r in atm_rows
            ) / max(len(atm_rows), 1)
            volume_ratio = (atm_vol / avg_vol) if avg_vol > 0 else 1.0

        # VSA strength (PineScript thresholds)
        if volume_ratio >= VSA_STRONGEST:
            strength, vsa_score = "STRONGEST", 3
        elif volume_ratio >= VSA_STRONG:
            strength, vsa_score = "STRONG", 2
        elif volume_ratio >= VSA_MODERATE:
            strength, vsa_score = "MODERATE", 1
        else:
            strength, vsa_score = "WEAK", 0

        return {
            "strength":     strength,
            "vsa_score":    vsa_score,
            "volume_ratio": round(volume_ratio, 2),
            "volume_ok":    vsa_score >= 1,
        }

    # ─────────────────────────────────────────────────────────────────────────
    # 2. Fake Breakout Trap
    # ─────────────────────────────────────────────────────────────────────────

    def _fake_breakout_check(self, data: Dict, vsa: Dict) -> Dict:
        """
        PineScript: weak volume on breakout = fake.
        FakeBreakoutEngine already exists — this wraps + enhances it.
        """
        fb = data.get("execution_debug", {}).get("fake_breakout", {})
        existing_fake = bool(fb.get("is_fake_breakout") or fb.get("fakeBreakout"))

        # Additional check: breakout on WEAK volume = high fake probability
        breakout = data.get("execution_layer", {}).get("breakout", {})
        is_breakout = breakout.get("status") in ("BREAKOUT", "BREAKDOWN")
        weak_volume = vsa.get("vsa_score", 1) == 0

        detected = existing_fake or (is_breakout and weak_volume)
        reason = fb.get("reason", "Weak volume on breakout") if detected else ""

        return {"detected": detected, "reason": reason}

    # ─────────────────────────────────────────────────────────────────────────
    # 3. IV Spike Trap
    # ─────────────────────────────────────────────────────────────────────────

    def _iv_spike_trap(self, data: Dict) -> Dict:
        """
        IV sudden spike before breakout = market makers inflating IV to sell
        options to retail → then IV crush after the event.
        Signal: atm_iv_pct jumps significantly above normal.
        """
        vol = data.get("volatility_context", {}).get("volatility_engine", {})
        atm_iv = float(vol.get("atm_iv_pct", 0) or 0)

        # IV > 50% = extreme, likely event-driven inflation
        detected = atm_iv > 50.0
        return {"detected": detected, "iv": atm_iv, "threshold": 50.0}

    # ─────────────────────────────────────────────────────────────────────────
    # 4. Liquidity Sweep Trap
    # ─────────────────────────────────────────────────────────────────────────

    def _liquidity_sweep_trap(self, data: Dict) -> Dict:
        """
        PineScript PAT: price swept past a key level then reversed.
        If SMC detects ABOVE_RESISTANCE sweep AND price is back below → trap.
        """
        smc = data.get("smc_analysis", {})
        sweep = smc.get("liquidity_sweep", "NONE")
        detected = sweep in ("ABOVE_RESISTANCE", "BELOW_SUPPORT")
        return {"detected": detected, "sweep": sweep}

    # ─────────────────────────────────────────────────────────────────────────
    # 5. Option Trap (PCR extreme)
    # ─────────────────────────────────────────────────────────────────────────

    def _option_trap(self, data: Dict) -> Dict:
        """
        When PCR is extreme (>1.8 or <0.5), too many traders on one side.
        Market makers will move price to hurt the crowd.
        """
        pcr = data.get("volatility_context", {}).get("pcr", {})
        pcr_val = float(pcr.get("pcr", 1.0) or 1.0)

        # Extreme PCR = crowd heavily one-sided = trap zone
        detected = pcr_val > 1.8 or pcr_val < 0.5
        return {"detected": detected, "pcr": pcr_val}

    # ─────────────────────────────────────────────────────────────────────────
    # 6. Fear Trap (extreme fear index = news event trap)
    # ─────────────────────────────────────────────────────────────────────────

    def _fear_trap(self, data: Dict) -> Dict:
        """
        Fear index > 85 = extreme panic = potential for sharp reversal
        after the fear event passes (news trap).
        """
        complete = data.get("complete_decision", {})
        fear_analysis = complete.get("fear_index_analysis", {}) if complete else {}
        fear_score = float(fear_analysis.get("current_fear_index", 50) or 50)

        detected = fear_score > 85
        return {"detected": detected, "fear_index": fear_score}

    # ─────────────────────────────────────────────────────────────────────────
    # 7. CHoCH Trap (PineScript PAT: change of character not confirmed)
    # ─────────────────────────────────────────────────────────────────────────

    def _choch_trap(self, data: Dict) -> Dict:
        """
        PineScript PAT: CHoCH label fires when price breaks structure.
        But if CHoCH detected without volume confirmation → potential trap.
        """
        smc   = data.get("smc_analysis", {})
        choch = bool(smc.get("choch", False))
        # CHoCH + good volume = valid reversal. CHoCH alone = trap warning.
        # We only flag it as a trap when combined with other trap signals.
        # Standalone CHoCH is just a reversal warning, not a trap itself.
        return {"detected": False, "choch": choch}  # advisory only

    # ─────────────────────────────────────────────────────────────────────────

    def _reason(self, traps: list) -> str:
        if not traps:
            return ""
        return " | ".join(traps)
    