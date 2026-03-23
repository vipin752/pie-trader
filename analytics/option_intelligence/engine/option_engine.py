"""
PIE TRADER — option_intelligence/engine/option_engine.py  v5.1  FINAL

Complete Intelligence Pipeline (document architecture):
  Market State (Kafka)
    ↓ SMC / ICT Engine         ← PRICE ACTION CORE (entry model)
    ↓ Options Engine           ← CONTEXT model (Gamma/IV/OI)
    ↓ Volatility Engine
    ↓ Timeframe Engine
    ↓ Scoring Engine           ← IntelligenceScore (100 pts) — THE BRAIN
    ↓ Decision Engine
    ↓ Execution Plan
    ↓ Kafka → Java

Zero regression guarantee:
  All existing method calls preserved exactly:
    risk_engine.apply()
    position_engine.manage()
    scaling_engine.scale()
    exit_engine.manage_exit()
    FinalDecisionEngine(data, dte)
    adaptive_engine.apply_to_execution() / record_trade()
    strike_optimizer.evaluate()          ← static
"""

from __future__ import annotations
from datetime import datetime
from typing import Dict, Any, Optional

from option_intelligence.config.contract_config import get_contract

# ── CORE ────────────────────────────────────────────────────────────────────
from option_intelligence.engine.atm_engine              import ATMEngine
from option_intelligence.engine.pcr_engine              import PCREngine
from option_intelligence.engine.gamma_engine            import GammaEngine
from option_intelligence.engine.gex_engine              import GEXEngine
from option_intelligence.engine.ladder_engine           import LadderEngine
from option_intelligence.engine.liquidity_engine        import LiquidityEngine
from option_intelligence.engine.volatility_engine       import VolatilityEngine
from option_intelligence.engine.probability_engine      import ProbabilityEngine
from option_intelligence.engine.compression_engine      import CompressionEngine
from option_intelligence.engine.pressure_engine         import PressureEngine
from option_intelligence.engine.expiry_engine           import ExpiryEngine
from option_intelligence.engine.session_engine          import SessionEngine
from option_intelligence.engine.dealer_inventory_engine import DealerInventoryEngine

# ── SMC / ICT + NEW ENGINES ─────────────────────────────────────────────────
from option_intelligence.engine.smc_engine        import SMCEngine
from option_intelligence.engine.delta_engine      import DeltaEngine
from option_intelligence.engine.vwap_engine       import VWAPEngine
from option_intelligence.engine.trap_engine       import TrapEngine
from option_intelligence.engine.timeframe_engine  import TimeframeEngine

# ── SCORING ENGINE (THE BRAIN) ───────────────────────────────────────────────
from option_intelligence.scoring.intelligence_score import IntelligenceScore

# ── ADVANCED ────────────────────────────────────────────────────────────────
from option_intelligence.engine.historical_engine         import HistoricalEngine
from option_intelligence.engine.liquidity_sweep_engine    import LiquiditySweepEngine
from option_intelligence.engine.oi_flow_engine            import OIFlowEngine
from option_intelligence.engine.volume_spike_engine       import VolumeSpikeEngine
from option_intelligence.engine.smart_money_flow_engine   import SmartMoneyFlowEngine

# ── SNIPER ──────────────────────────────────────────────────────────────────
from option_intelligence.engine.trading_window_engine    import TradingWindowEngine
from option_intelligence.engine.fake_breakout_engine     import FakeBreakoutEngine
from option_intelligence.engine.sniper_execution_engine  import SniperExecutionEngine

# ── EXECUTION ────────────────────────────────────────────────────────────────
from option_intelligence.engine.breakout_trigger_engine  import BreakoutTriggerEngine
from option_intelligence.engine.candle_confirmation_engine import CandleConfirmationEngine
from option_intelligence.engine.final_execution_engine   import FinalExecutionEngine

# ── INTELLIGENCE ─────────────────────────────────────────────────────────────
from option_intelligence.engine.premium_intelligence_engine import PremiumIntelligenceEngine
from option_intelligence.engine.strike_selection_engine    import StrikeSelectionEngine
from option_intelligence.engine.trade_signal_engine        import TradeSignalEngine
from option_intelligence.engine.auto_trade_decision_engine import AutoTradeDecisionEngine
from option_intelligence.engine.execution_timing_engine    import ExecutionTimingEngine
from option_intelligence.engine.risk_management_engine     import RiskManagementEngine
from option_intelligence.engine.position_management_engine import PositionManagementEngine
from option_intelligence.engine.confidence_engine          import ConfidenceEngine
from option_intelligence.engine.exit_engine                import ExitEngine
from option_intelligence.engine.adaptive_learning_engine   import AdaptiveLearningEngine
from option_intelligence.engine.scaling_engine             import ScalingEngine
from option_intelligence.engine.strike_optimizer           import StrikeOptimizer

# ── FINAL ────────────────────────────────────────────────────────────────────
from option_intelligence.engine.final_decision_engine import FinalDecisionEngine


class OptionEngine:

    def __init__(self):
        self.previous_spot = None
        self.previous_rows = None

        # CORE
        self.atm_engine              = ATMEngine()
        self.pcr_engine              = PCREngine()
        self.gamma_engine            = GammaEngine()
        self.gex_engine              = GEXEngine()
        self.ladder_engine           = LadderEngine()
        self.liquidity_engine        = LiquidityEngine()
        self.vol_engine              = VolatilityEngine()
        self.prob_engine             = ProbabilityEngine()
        self.compression_engine      = CompressionEngine()
        self.pressure_engine         = PressureEngine()
        self.expiry_engine           = ExpiryEngine()
        self.session_engine          = SessionEngine()
        self.dealer_inventory_engine = DealerInventoryEngine()

        # SMC / ICT + NEW
        self.smc_engine       = SMCEngine()
        self.delta_engine     = DeltaEngine()
        self.vwap_engine      = VWAPEngine()
        self.trap_engine      = TrapEngine()
        self.timeframe_engine = TimeframeEngine()

        # SCORING ENGINE (the brain)
        self.scoring_engine = IntelligenceScore()

        # ADVANCED
        self.historical_engine       = HistoricalEngine()
        self.sweep_engine            = LiquiditySweepEngine()
        self.oi_flow_engine          = OIFlowEngine()
        self.volume_spike_engine     = VolumeSpikeEngine()
        self.smart_money_flow_engine = SmartMoneyFlowEngine()

        # SNIPER
        self.trading_window_engine   = TradingWindowEngine()
        self.fake_breakout_engine    = FakeBreakoutEngine()
        self.sniper_execution_engine = SniperExecutionEngine()

        # EXECUTION
        self.breakout_engine         = BreakoutTriggerEngine()
        self.candle_engine           = CandleConfirmationEngine()
        self.final_execution_engine  = FinalExecutionEngine()

        # INTELLIGENCE
        self.premium_engine          = PremiumIntelligenceEngine()
        self.strike_engine           = StrikeSelectionEngine()
        self.trade_signal_engine     = TradeSignalEngine()
        self.auto_trade_engine       = AutoTradeDecisionEngine()
        self.execution_timing_engine = ExecutionTimingEngine()
        self.risk_engine             = RiskManagementEngine()
        self.position_engine         = PositionManagementEngine()
        self.confidence_engine       = ConfidenceEngine()
        self.exit_engine             = ExitEngine()
        self.adaptive_engine         = AdaptiveLearningEngine()
        self.scaling_engine          = ScalingEngine()
        self.strike_optimizer        = StrikeOptimizer()

    # ─────────────────────────────────────────────────────────────────────────
    # MAIN ANALYZE
    # ─────────────────────────────────────────────────────────────────────────

    def analyze(self,
                spot: float,
                rows: list,
                symbol: str,
                expiry_dates=None,
                market_state: Optional[Dict] = None) -> Dict[str, Any]:
        """
        Full intelligence pipeline.

        Args:
            spot:         Current index spot price
            rows:         Option chain strike rows
            symbol:       NIFTY / BANKNIFTY / FINNIFTY / MIDCPNIFTY
            expiry_dates: List of expiry date strings
            market_state: Optional Java MarketState (pie.market.state)
                          Provides: vwap, pdh, pdl, regime, weekly_high/low
        """
        symbol = str(symbol).upper()
        if not rows or spot == 0:
            return {"error": "empty_chain", "symbol": symbol}

        contract = get_contract(symbol)

        # ── CORE ─────────────────────────────────────────────────────────────
        atm, filtered = self.atm_engine.filter_atm_window(spot, rows, symbol)
        pcr          = self.pcr_engine.calculate(filtered)            or {}
        gamma        = self.gamma_engine.calculate_exposure(filtered, spot) or {}
        gex          = self.gex_engine.calculate(filtered, spot, symbol)    or {}
        ladder       = self.ladder_engine.detect_clusters(filtered)   or []
        liquidity    = self.liquidity_engine.zones(ladder, spot)      or {}
        volatility   = self.vol_engine.expected_move(spot, filtered)  or {}
        probability  = self.prob_engine.calculate(spot, gamma, pcr, gex) or {}
        compression  = self.compression_engine.detect(spot, gamma, ladder) or {}
        pressure     = self.pressure_engine.calculate(pcr, gamma, gex, probability, compression) or {}
        expiry       = self.expiry_engine.analyze(symbol, expiry_dates) or {}
        session      = self.session_engine.classify()                 or {}
        dealer_inventory = self.dealer_inventory_engine.calculate(spot, gamma, gex, pressure) or {}

        # ── ADVANCED ─────────────────────────────────────────────────────────
        flows       = self.oi_flow_engine.detect(rows, self.previous_rows) or []
        spikes      = self.volume_spike_engine.detect(rows)                or []
        smart_money = self.smart_money_flow_engine.detect(rows, spot)      or {}
        sweep       = self.sweep_engine.detect(spot, ladder, gamma)        or {}
        historical  = self.historical_engine.calculate(pcr, spot, symbol)  or {}
        self.previous_rows = rows

        # ── PRICE MEMORY ──────────────────────────────────────────────────────
        prev_spot    = self.previous_spot if self.previous_spot is not None else spot
        price_action = {"current": spot, "previous": prev_spot, "change": spot - prev_spot}
        self.previous_spot = spot

        # ── LIQUIDITY LEVELS (safe fallback) ─────────────────────────────────
        liquidity_sr = liquidity or {
            "support":    spot - contract.get("strike_gap", 50),
            "resistance": spot + contract.get("strike_gap", 50),
        }

        # ── SMC / ICT ENGINE (price action core) ─────────────────────────────
        smc_result = {}
        try:
            smc_result = self.smc_engine.analyze(
                spot, rows, liquidity_sr, prev_spot
            ) or {}
        except Exception as e:
            smc_result = {"bias": "NEUTRAL", "signal": "NEUTRAL", "error": str(e)}

        # ── DELTA ENGINE ─────────────────────────────────────────────────────
        delta = {}
        try:
            delta = self.delta_engine.calculate(rows, price_action) or {}
        except Exception as e:
            delta = {"delta_bias": "NEUTRAL", "error": str(e)}

        # ── VWAP ENGINE ───────────────────────────────────────────────────────
        vwap_result = {}
        try:
            vwap_result = self.vwap_engine.calculate(spot, rows, market_state) or {}
        except Exception as e:
            vwap_result = {"vwap_bias": "NEUTRAL", "error": str(e)}

        # ── CONTEXT (available to downstream engines) ────────────────────────
        ctx: Dict[str, Any] = {
            "market_context": {
                "symbol": symbol, "spot": spot, "atm": atm,
                "contract": contract, "session": session,
            },
            "dealer_positioning": {
                "dealer_inventory_model": dealer_inventory,
                "gamma": gamma,
            },
            "liquidity_map": {"support_resistance": liquidity_sr},
            "market_structure": {
                "probability_model": probability,
                "compression":       compression,
                "pressure":          pressure,
            },
            "institutional_flow": {
                "volume_spike_engine": {
                    "spikes": spikes if isinstance(spikes, list) else []
                }
            },
            "smc_analysis":   smc_result,
            "delta_analysis": delta,
            "vwap_analysis":  vwap_result,
            "price_action":   price_action,
        }

        # ── SNIPER ────────────────────────────────────────────────────────────
        ctx["trading_window"]   = self.trading_window_engine.calculate(ctx) or {"window": "UNKNOWN"}
        ctx["fake_breakout"]    = self.fake_breakout_engine.calculate(ctx)  or {"is_fake_breakout": False}
        ctx["sniper_execution"] = self.sniper_execution_engine.calculate(ctx) or {}

        # ── EXECUTION ─────────────────────────────────────────────────────────
        breakout             = self.breakout_engine.detect(ctx)
        ctx["breakout"]      = breakout
        confirmation         = self.candle_engine.confirm(breakout, ctx)
        ctx["confirmation"]  = confirmation
        final_execution      = self.final_execution_engine.decide(
            ctx, rows, self.strike_engine, self.sniper_execution_engine
        )
        ctx["final_execution"] = final_execution

        # ── INTELLIGENCE ─────────────────────────────────────────────────────
        premium         = self.premium_engine.analyze(ctx)  or {}
        ctx["premium_intelligence"] = premium
        strike_selection = self.strike_engine.select(ctx)   or {}
        ctx["strike_selection"] = strike_selection
        strike_optimizer = self.strike_optimizer.evaluate(ctx)
        ctx["strike_optimizer"] = strike_optimizer
        optimized        = strike_optimizer.get("optimized_strike", {})
        final_strike     = optimized.get("final_strike")
        if final_strike:
            strike_selection["selected_strike"] = final_strike
            strike_selection["optimized"]       = True
            strike_selection["base_strike"]     = optimized.get("base_strike")

        trade_signal = self.trade_signal_engine.generate(ctx) or {}
        if final_execution.get("execution_ready"):
            trade_signal = {
                "strategy":   "EXECUTE",
                "reason":     final_execution.get("reason"),
                "confidence": "VERY_HIGH",
            }

        # ── BASE OUTPUT ───────────────────────────────────────────────────────
        final_output: Dict[str, Any] = {
            "trade_signal":         trade_signal,
            "premium_intelligence": premium,
            "strike_selection":     strike_selection,
            "strike_optimizer":     strike_optimizer,
            "dealer_positioning": {
                "dealer_inventory_model": dealer_inventory,
                "gex": gex, "gamma": gamma,
            },
            "market_structure": {
                "compression":       compression,
                "pressure":          pressure,
                "probability_model": probability,
            },
            "liquidity_map": {
                "support_resistance":  liquidity,
                "oi_ladder_clusters":  ladder,
                "liquidity_sweep":     sweep,
            },
            "institutional_flow": {
                "smart_money_flow_engine": smart_money,
                "oi_flow_engine":          flows,
                "volume_spike_engine":     spikes,
            },
            "volatility_context": {
                "volatility_engine": volatility,
                "pcr":               pcr,
            },
            "market_context": {
                "symbol": symbol, "spot": spot, "atm": atm,
                "contract": contract, "expiry": expiry, "session": session,
            },
            "historical_context": historical,
            "execution_debug": {
                "trading_window": ctx.get("trading_window"),
                "fake_breakout":  ctx.get("fake_breakout"),
            },
            "execution_layer": {
                "breakout":        breakout,
                "confirmation":    confirmation,
                "final_execution": final_execution,
            },
            # New engine outputs
            "smc_analysis":   smc_result,
            "delta_analysis": delta,
            "vwap_analysis":  vwap_result,
        }

        # ── TRAP ANALYSIS ────────────────────────────────────────────────────
        trap = {}
        try:
            trap = self.trap_engine.detect(final_output, rows) or {}
        except Exception as e:
            trap = {"trap_detected": False, "avoid_trade": False, "error": str(e)}
        final_output["trap_analysis"] = trap

        # ── TIMEFRAME ANALYSIS ────────────────────────────────────────────────
        tf_result = {}
        try:
            tf_result = self.timeframe_engine.analyze(spot, final_output, market_state) or {}
        except Exception as e:
            tf_result = {"timeframe_analysis": {}, "alignment": {}, "error": str(e)}
        final_output["timeframe_analysis"] = tf_result

        # ═════════════════════════════════════════════════════════════════════
        # SCORING ENGINE — runs BEFORE confidence/decision (feeds both)
        # ═════════════════════════════════════════════════════════════════════
        intelligence_score = {}
        try:
            intelligence_score = self.scoring_engine.calculate(final_output) or {}
        except Exception as e:
            intelligence_score = {
                "total_score": 0, "state": "NO_TRADE", "action": "NO_TRADE",
                "setup": "NONE", "direction": "NEUTRAL",
                "score_breakdown": {}, "error": str(e),
            }
        final_output["intelligence_score"] = intelligence_score

        # ── FULL PIPELINE (original method names — zero regression) ──────────

        # Confidence (uses scoring data + timeframe + delta + trap)
        final_output["confidence"] = self.confidence_engine.calculate(final_output)

        try:
            final_output["auto_trade_decision"] = self.auto_trade_engine.generate(final_output)
        except Exception as e:
            final_output["auto_trade_error"] = str(e)

        try:
            final_output["adaptive_params"] = self.adaptive_engine.apply_to_execution(final_output)
        except Exception as e:
            final_output["adaptive_error"] = str(e)

        try:
            final_output["execution_timing"] = self.execution_timing_engine.generate(final_output)
        except Exception as e:
            final_output["execution_timing_error"] = str(e)

        try:
            final_output["risk_management"] = self.risk_engine.apply(final_output)   # exact original
        except Exception as e:
            final_output["risk_error"] = str(e)

        try:
            final_output["position_management"] = self.position_engine.manage(final_output)  # exact original
        except Exception as e:
            final_output["position_error"] = str(e)

        try:
            final_output["scaling"] = self.scaling_engine.scale(final_output)        # exact original
        except Exception as e:
            final_output["scaling_error"] = str(e)

        try:
            final_output["exit_management"] = self.exit_engine.manage_exit(final_output)  # exact original
        except Exception as e:
            final_output["exit_error"] = str(e)

        try:
            dte          = self._calculate_dte(expiry)
            final_engine = FinalDecisionEngine(final_output, dte)   # exact original constructor
            decision     = final_engine.generate_complete_decision()
            final_output["complete_decision"] = decision
            final_output["ui_view"]           = decision
        except Exception as e:
            final_output["complete_decision_error"] = str(e)

        return final_output

    # ── Original helpers (preserved exactly) ─────────────────────────────────

    def on_trade_close(self, trade_result: Dict[str, Any]):
        try:
            self.adaptive_engine.record_trade(trade_result)
        except Exception:
            pass

    def _calculate_dte(self, expiry):
        try:
            expiry_date = expiry.get("nearest_expiry")
            if not expiry_date:
                return 6
            expiry_dt = datetime.strptime(expiry_date, "%Y-%m-%d")
            return max((expiry_dt - datetime.now()).days, 0)
        except Exception:
            return 6
        