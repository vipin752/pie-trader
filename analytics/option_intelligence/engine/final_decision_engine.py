from typing import Dict, Any
from datetime import datetime


class FinalDecisionEngine:

    def __init__(self, raw_data: Dict, dte: int):
        self.raw_data = raw_data
        self.dte = dte

        # ================= SAFE EXTRACTION =================
        self.market_ctx = raw_data.get("market_context", {})
        self.vol_ctx = raw_data.get("volatility_context", {})
        self.gamma_ctx = raw_data.get("dealer_positioning", {})
        self.flow_ctx = raw_data.get("institutional_flow", {})
        self.execution_debug = raw_data.get("execution_debug", {})

        self.spot = self.market_ctx.get("spot", 0)

        # 🚨 HARD GUARD (NO SILENT FAILURE)
        if "liquidity_map" not in raw_data:
            raise ValueError("CRITICAL: liquidity_map missing in FinalDecisionEngine input")

    # ================= MAIN =================
    def generate_complete_decision(self):

        if self.execution_debug.get("fake_breakout", {}).get("is_fake_breakout"):
            return self._no_trade_response("Fake breakout")

        fear = self._build_fear_index()
        session = self._build_session()
        probability = self._build_probability()
        btst = self._build_btst()
        card = self._build_trading_card(btst, fear)

        return {
            "timestamp": datetime.utcnow().isoformat(),
            "symbol": self.market_ctx.get("symbol"),
            "spot": self.spot,
            "fear_index_analysis": fear,
            "session_classification": session,
            "probability_matrix": probability,
            "btst_analysis": btst,
            "trading_card": card
        }

    # ================= NO TRADE =================
    def _no_trade_response(self, reason: str):
        return {
            "timestamp": datetime.utcnow().isoformat(),
            "symbol": self.market_ctx.get("symbol"),
            "spot": self.spot,
            "fear_index_analysis": self._build_fear_index(),
            "session_classification": self._build_session(),
            "probability_matrix": {},
            "btst_analysis": {"best_btst": None},
            "trading_card": {
                "spot": self.spot,
                "fear_index": None,
                "session_1": "NO TRADE",
                "session_3": "NO TRADE",
                "btst": "NO BTST"
            }
        }

    # ================= FEAR INDEX =================
    def _build_fear_index(self):

        pcr = self._pcr_component()
        iv = self._iv_component()
        gamma = self._gamma_component()
        volume = self._volume_component()
        skew = 50

        fear = (pcr + iv + gamma + volume + skew) / 5

        return {
            "current_fear_index": round(fear, 2),
            "zone": self._zone(fear),
            "recommended_action": self._action(fear),
            "components": {
                "pcr": {"value": pcr},
                "iv": {"value": iv},
                "gamma": {"value": gamma},
                "volume": {"value": volume},
                "skew": {"value": skew}
            }
        }

    # ================= COMPONENTS =================
    def _pcr_component(self):
        pcr = self.vol_ctx.get("pcr", {}).get("pcr", 1)
        return max(0, min(100, 50 + (pcr - 1) * 40))

    def _iv_component(self):
        iv = self.vol_ctx.get("volatility_engine", {}).get("atm_iv_pct", 15)
        return max(0, min(100, (iv / 30) * 100))

    def _gamma_component(self):
        gamma = abs(self.gamma_ctx.get("gamma", {}).get("net_gamma", 0))
        return max(0, min(100, gamma * 2))

    def _volume_component(self):
        spikes = self.flow_ctx.get("volume_spike_engine", {}).get("spikes", [])
        return max(0, min(100, len(spikes) * 5))

    # ================= HELPERS =================
    def _zone(self, value):
        if value > 70:
            return "FEAR"
        elif value < 30:
            return "GREED"
        return "NEUTRAL"

    def _action(self, value):
        if value > 70:
            return "PREPARE_BREAKOUT"
        elif value < 30:
            return "CAUTION_LONG"
        return "FOLLOW_MARKET"

    # ================= SESSION =================
    def _build_session(self):
        return {
            "current_session": "post_market",
            "next_session": "morning_breakout"
        }

    # ================= PROBABILITY =================
    def _build_probability(self):

        if self.execution_debug.get("fake_breakout", {}).get("is_fake_breakout"):
            return {}

        return (
            self.raw_data
            .get("market_structure", {})
            .get("probability_model", {})
        )
    # ================= BTST =================
    def _build_btst(self):
        return {"best_btst": None}

    # ================= TRADING CARD (FINAL FIXED) =================
    def _build_trading_card(self, btst_data, fear_data):

        levels = self.raw_data.get("liquidity_map", {}).get("support_resistance")

        # 🚨 FAIL SAFE
        if not levels or not levels.get("support") or not levels.get("resistance"):
            return {
                "spot": self.spot,
                "fear_index": fear_data.get("current_fear_index"),
                "session_1": "DATA ERROR",
                "session_3": "CHECK LIQUIDITY_MAP",
                "btst": "NO BTST"
            }

        call_wall = levels["resistance"]
        put_wall = levels["support"]

        contract = self.market_ctx.get("contract", {})
        strike_gap = contract.get("strike_gap", 50)

        return {
            "spot": self.spot,
            "fear_index": fear_data.get("current_fear_index"),

            "session_1": f"Gap up → {call_wall} CE / Gap down → {put_wall} PE",

            # ✅ FINAL CORRECT LOGIC
            "session_3": (
                f"Break {call_wall} → {call_wall + strike_gap} CE / "
                f"Break {put_wall} → {put_wall - strike_gap} PE"
            ),

            "btst": "NO BTST"
        }
        