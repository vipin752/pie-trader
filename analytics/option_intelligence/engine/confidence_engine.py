"""
PIE TRADER — option_intelligence/engine/confidence_engine.py

Updated scoring model adding:
  - Timeframe alignment score   (document requirement, section 5)
  - Delta confirmation score
  - VWAP alignment score
  - SMC/VSA trap penalty

Document scoring table:
  Monthly+Weekly+Daily aligned → +15
  Weekly+Daily aligned         → +10
  Daily+Intraday aligned       → +8
  Only intraday                → +5
  Opposite higher TF           → -10

Total score breakdown (100 points):
  Premium activity  : 0-25
  Gamma (negative)  : 0-20
  Compression       : 0-20
  Flip distance     : 0-15
  Window            : 0-10
  Fake breakout     : 0-10
  ─────────────────── = 100 (original)

  NEW additions (can push score above 100 for cap at VERY_HIGH):
  Timeframe alignment : -10 to +15
  Delta confirmation  : 0-10
  VWAP alignment      : 0-5
  Trap penalty        : 0 to -20

Existing thresholds preserved (no regression):
  >= 85 → VERY_HIGH
  >= 75 → HIGH
  >= 60 → MEDIUM
  default → LOW
"""

from typing import Dict, Any


class ConfidenceEngine:

    def calculate(self, data: Dict[str, Any]) -> Dict[str, Any]:
        try:
            score    = 0
            breakdown = {}

            premium     = data.get("premium_intelligence", {})
            gamma       = data.get("dealer_positioning", {}).get("gamma", {})
            compression = data.get("market_structure", {}).get("compression", {})
            execution   = data.get("execution_debug", {})

            # ── ORIGINAL SCORING (preserved exactly — zero regression) ─────

            # Premium (0-25)
            premium_score = 25 if premium.get("premium_activity") == "HIGH" else 0
            score        += premium_score
            breakdown["premium"] = premium_score

            # Gamma — negative net gamma = short gamma = trending = edge (0-20)
            gamma_score = 20 if gamma.get("net_gamma", 0) < 0 else 0
            score       += gamma_score
            breakdown["gamma"] = gamma_score

            # Compression (0-20)
            compression_score = 20 if compression.get("compression_detected") else 0
            score            += compression_score
            breakdown["compression"] = compression_score

            # Flip distance < 0.1% of spot (0-15)
            flip_score = 15 if compression.get("flip_distance_pct", 1) < 0.1 else 0
            score      += flip_score
            breakdown["flip_distance"] = flip_score

            # Trading window (0-10)
            window_score = 10 if execution.get("trading_window", {}).get("window") in [
                "EXPANSION", "THETA"
            ] else 0
            score        += window_score
            breakdown["window"] = window_score

            # Fake breakout guard (0-10)
            fake_score = 10 if not execution.get("fake_breakout", {}).get("is_fake_breakout") else 0
            score      += fake_score
            breakdown["fake_breakout"] = fake_score

            # ── NEW: TIMEFRAME ALIGNMENT (document section 5) ──────────────
            tf = data.get("timeframe_analysis", {})
            alignment = tf.get("alignment", {}) if tf else {}
            tf_score  = alignment.get("alignment_score", 0) or 0
            score    += tf_score
            breakdown["timeframe_alignment"] = tf_score

            # ── NEW: DELTA CONFIRMATION (0-10) ────────────────────────────
            delta = data.get("delta_analysis", {})
            delta_score = 0
            if delta.get("delta_bias") in ("BEARISH", "BULLISH") and \
               delta.get("confidence") == "HIGH":
                delta_score = 10
            elif delta.get("delta_bias") in ("BEARISH", "BULLISH"):
                delta_score = 5
            score    += delta_score
            breakdown["delta"] = delta_score

            # ── NEW: VWAP ALIGNMENT (0-5) ─────────────────────────────────
            vwap = data.get("vwap_analysis", {})
            vwap_score = 5 if vwap.get("vwap_bias") in ("BULLISH", "BEARISH") else 0
            score     += vwap_score
            breakdown["vwap"] = vwap_score

            # ── NEW: TRAP PENALTY (0 to -20) ─────────────────────────────
            trap = data.get("trap_analysis", {})
            trap_score = 0
            if trap.get("avoid_trade"):
                trap_score = -20
            elif trap.get("trap_detected"):
                trap_score = -10
            score    += trap_score
            breakdown["trap_penalty"] = trap_score

            # Cap at 0 minimum
            score = max(0, score)

            return {
                "confidence_score": score,
                "confidence_level": self._level(score),
                "breakdown":        breakdown,
            }

        except Exception as e:
            return {
                "confidence_score": 0,
                "confidence_level": "LOW",
                "error": str(e),
            }

    def _level(self, score: int) -> str:
        # Thresholds unchanged — backward compatible
        if score >= 85:
            return "VERY_HIGH"
        elif score >= 75:
            return "HIGH"
        elif score >= 60:
            return "MEDIUM"
        return "LOW"
    