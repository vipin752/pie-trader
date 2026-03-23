package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.GammaDTO;
import com.pietrader.dto.execution.BreakoutDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.market.CompressionDTO;
import com.pietrader.dto.market.ExpiryDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.signal.StrikeCandidateDTO;
import com.pietrader.dto.signal.StrikeSelectionDTO;
import com.pietrader.dto.volatility.VolatilityEngineDTO;
import com.pietrader.execution.BreakoutTriggerEngine;
import com.pietrader.execution.TradeStateMachine;
import com.pietrader.execution.model.TriggerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PIE TRADER — TradeCardMapper
 *
 * Converts full OptionAnalyticsDTO (80+ engine output) into the
 * clean Trade Card structure shown to the trader.
 *
 * All DTO method paths verified against actual DTO class definitions:
 *   GammaDTO.callGammaWall / putGammaWall / gammaFlip / netGamma
 *   DealerInventoryModelDTO.gammaFlip / dealerInventory / volatilityRegime
 *   StrikeSelectionDTO.selectedStrike / topCandidates
 *   StrikeCandidateDTO.ltp / iv / volume
 *   ExecutionLayerDTO.breakout.triggerPrice / finalExecution.executionReady
 *   ExecutionTimingDTO.entrySignal / entryType / reason
 *   CompressionDTO.compressionDetected / breakoutSignal
 *   ExpiryDTO.daysToExpiry / phase / nearestExpiry
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeCardMapper {

    private final TradeStateMachine      stateMachine;
    private final BreakoutTriggerEngine  triggerEngine;

    @Value("${trading.lot.size:75}")     private int    lotSize;
    @Value("${trading.capital:200000}")  private double capital;
    @Value("${trading.sl.percent:25.0}") private double slPct;

    /**
     * Main entry — returns null-safe trade card map.
     * Never throws; always returns a usable map.
     */
    public Map<String, Object> toTradeCard(OptionAnalyticsDTO dto) {
	Map<String, Object> card = new LinkedHashMap<>();
	if (dto == null) {
	    card.put("status", "NO_DATA");
	    return card;
	}

	try {
	    // ── Core identity ──────────────────────────────────────────────
	    MarketContextDTO ctx = dto.getMarketContext();
	    String symbol = ctx != null && ctx.getSymbol() != null ? ctx.getSymbol() : "NIFTY";
	    Double spot   = ctx != null ? ctx.getSpot()   : null;
	    Integer atm   = ctx != null ? ctx.getAtm()    : null;

	    card.put("symbol",     symbol);
	    card.put("spot",       spot   != null ? spot   : 0.0);
	    card.put("atm",        atm    != null ? atm    : 0);
	    card.put("timestamp",  System.currentTimeMillis());

	    // ── Regime + strategy ──────────────────────────────────────────
	    String regime    = resolveRegime(dto);
	    String strategy  = resolveStrategy(dto);
	    String action    = resolveAction(dto);
	    String direction = resolveDirection(dto);

	    card.put("regime",    regime);
	    card.put("strategy",  strategy);
	    card.put("action",    action);
	    card.put("direction", direction);
	    card.put("confidence", resolveConfidence(dto));

	    // ── Market session ─────────────────────────────────────────────
	    card.put("session",   resolveSession(dto));
	    card.put("expiry",    resolveExpiry(dto));

	    // ── Key levels ─────────────────────────────────────────────────
	    card.put("levels", buildLevels(dto));

	    // ── Execution window ───────────────────────────────────────────
	    card.put("execution", buildExecution(dto));

	    // ── Option card ────────────────────────────────────────────────
	    card.put("option", buildOption(dto));

	    // ── Risk card ──────────────────────────────────────────────────
	    card.put("risk", buildRisk(dto));

	    // ── Intelligence signals ───────────────────────────────────────
	    card.put("signals", buildSignals(dto));

	    // ── Reason bullets ─────────────────────────────────────────────
	    card.put("reason", buildReasons(dto));

	    // ── Fear index ─────────────────────────────────────────────────
	    card.put("fear_index", buildFearIndex(dto));

	    // ── Trading card (session guidance) ───────────────────────────
	    card.put("trading_card", buildTradingCard(dto));

	    // ── Execution plan (NEW — state machine + trigger engine) ──────
	    card.put("execution_plan", buildExecutionPlan(dto, symbol));

	    card.put("status", "OK");

	} catch (Exception e) {
	    log.error("❌ TradeCardMapper error: {}", e.getMessage(), e);
	    card.put("status", "ERROR");
	    card.put("error", e.getMessage());
	}

	return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEVELS
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildLevels(OptionAnalyticsDTO dto) {
	Map<String, Object> levels = new LinkedHashMap<>();

	// Support / Resistance from liquidity map
	if (dto.getLiquidityMap() != null && dto.getLiquidityMap().getSupportResistance() != null) {
	    var sr = dto.getLiquidityMap().getSupportResistance();
	    levels.put("support",    sr.getSupport());
	    levels.put("resistance", sr.getResistance());
	} else {
	    levels.put("support",    0);
	    levels.put("resistance", 0);
	}

	// Gamma levels from GammaDTO (verified: callGammaWall, putGammaWall, gammaFlip)
	GammaDTO gamma = resolveGamma(dto);
	if (gamma != null) {
	    levels.put("gamma_flip",  gamma.getGammaFlip()    != null ? gamma.getGammaFlip()    : 0.0);
	    levels.put("call_wall",   gamma.getCallGammaWall() != null ? gamma.getCallGammaWall() : 0);
	    levels.put("put_wall",    gamma.getPutGammaWall()  != null ? gamma.getPutGammaWall()  : 0);
	    levels.put("net_gamma",   gamma.getNetGamma()      != null ? gamma.getNetGamma()      : 0.0);
	} else {
	    // Fallback to DealerInventoryModel gammaFlip
	    DealerInventoryModelDTO inv = resolveInventory(dto);
	    levels.put("gamma_flip", inv != null && inv.getGammaFlip() != null ? inv.getGammaFlip() : 0.0);
	    levels.put("call_wall",  0);
	    levels.put("put_wall",   0);
	    levels.put("net_gamma",  0.0);
	}

	// IV levels
	if (dto.getVolatilityContext() != null) {
	    VolatilityEngineDTO ve = dto.getVolatilityContext().getVolatilityEngine();
	    if (ve != null) {
		levels.put("upper_1d", ve.getUpper1d() != null ? ve.getUpper1d() : 0.0);
		levels.put("lower_1d", ve.getLower1d() != null ? ve.getLower1d() : 0.0);
		levels.put("atm_iv",   ve.getAtmIvPct() != null ? ve.getAtmIvPct() : 0.0);
	    }
	}

	return levels;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXECUTION WINDOW
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildExecution(OptionAnalyticsDTO dto) {
	Map<String, Object> exec = new LinkedHashMap<>();

	ExecutionLayerDTO layer = dto.getExecutionLayer();
	ExecutionTimingDTO timing = dto.getExecutionTiming();

	// Entry trigger from breakout DTO
	String triggerPrice = null;
	boolean executionReady = false;
	String blockReason = "No breakout confirmed";
	String breakoutStatus = "WAIT";

	if (layer != null) {
	    BreakoutDTO bo = layer.getBreakout();
	    if (bo != null) {
		triggerPrice   = bo.getTriggerPrice();
		breakoutStatus = bo.getStatus() != null ? bo.getStatus() : "WAIT";
	    }
	    if (layer.getFinalExecution() != null) {
		executionReady = Boolean.TRUE.equals(layer.getFinalExecution().getExecutionReady());
		if (!executionReady && layer.getFinalExecution().getReason() != null) {
		    blockReason = layer.getFinalExecution().getReason();
		}
	    }
	}

	// Format trigger price: "22500/23000" → "Break 23000 → BUY CE | Break 22500 → BUY PE"
	exec.put("entry_trigger",  formatTrigger(triggerPrice));
	exec.put("raw_trigger",    triggerPrice != null ? triggerPrice : "—");
	exec.put("breakout_status", breakoutStatus);

	if (timing != null) {
	    exec.put("entry_signal",   timing.getEntrySignal()  != null ? timing.getEntrySignal()  : "WAIT");
	    exec.put("entry_type",     timing.getEntryType()    != null ? timing.getEntryType()    : "—");
	    exec.put("timing_reason",  timing.getReason()       != null ? timing.getReason()       : "—");
	} else {
	    exec.put("entry_signal",  "WAIT");
	    exec.put("entry_type",    "—");
	    exec.put("timing_reason", "—");
	}

	exec.put("execution_ready",  executionReady);
	exec.put("block_reason",     executionReady ? "—" : blockReason);
	exec.put("confirmation",     "Volume + Breakout + Structure");

	return exec;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OPTION CARD
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildOption(OptionAnalyticsDTO dto) {
	Map<String, Object> option = new LinkedHashMap<>();

	// Selected strike from StrikeSelectionDTO (verified: getSelectedStrike())
	String selected = null;
	double ltp = 0.0, iv = 0.0;
	long   volume = 0L;

	StrikeSelectionDTO sel = dto.getStrikeSelection();
	if (sel != null) {
	    selected = sel.getSelectedStrike();
	    // Top candidate [0] has ltp, iv, volume (StrikeCandidateDTO)
	    if (sel.getTopCandidates() != null && !sel.getTopCandidates().isEmpty()) {
		StrikeCandidateDTO top = sel.getTopCandidates().get(0);
		if (top != null) {
		    ltp    = top.getLtp()    != null ? top.getLtp()    : 0.0;
		    iv     = top.getIv()     != null ? top.getIv()     : 0.0;
		    volume = top.getVolume() != null ? top.getVolume() : 0L;
		}
	    }
	}

	// Fallback to action.option if strike selection empty
	if (selected == null && dto.getAutoTradeDecision() != null
		&& dto.getAutoTradeDecision().getAutoTradeAction() != null) {
	    selected = dto.getAutoTradeDecision().getAutoTradeAction().getOption();
	}

	// Optimized strike (verify override type and final strike)
	String finalStrike  = selected;
	String overrideType = "NONE";
	if (dto.getStrikeOptimizer() != null && dto.getStrikeOptimizer().getOptimizedStrike() != null) {
	    var opt = dto.getStrikeOptimizer().getOptimizedStrike();
	    if (opt.getFinalStrike() != null) finalStrike  = opt.getFinalStrike();
	    if (opt.getOverrideType() != null) overrideType = opt.getOverrideType();
	}

	option.put("selected",       selected    != null ? selected    : "—");
	option.put("final_strike",   finalStrike != null ? finalStrike : "—");
	option.put("override_type",  overrideType);
	option.put("ltp",            ltp);
	option.put("iv",             Math.round(iv * 100.0) / 100.0);
	option.put("volume",         volume);
	option.put("volume_rank",    volume > 0 ? "1" : "—");

	// PCR context
	if (dto.getVolatilityContext() != null && dto.getVolatilityContext().getPcr() != null) {
	    var pcr = dto.getVolatilityContext().getPcr();
	    option.put("pcr",           pcr.getPcr()       != null ? pcr.getPcr()       : 0.0);
	    option.put("pcr_sentiment", pcr.getSentiment() != null ? pcr.getSentiment() : "—");
	}

	return option;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RISK CARD
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildRisk(OptionAnalyticsDTO dto) {
	Map<String, Object> risk = new LinkedHashMap<>();

	// Position sizing (1 lot baseline)
	risk.put("position_size", "1 lot (" + lotSize + " qty)");
	risk.put("sl",            "Option SL " + (int)slPct + "%");
	risk.put("target",        "RR 1:2");
	risk.put("max_loss",      "1% capital (₹" + (int)(capital * 0.01) + ")");
	risk.put("capital",       capital);

	// Risk management from Python
	if (dto.getRiskManagement() != null) {
	    var rm = dto.getRiskManagement();
	    risk.put("position_status", rm.getPosition()   != null ? rm.getPosition()   : "NO_TRADE");
	    risk.put("risk_reason",     rm.getReason()     != null ? rm.getReason()     : "—");
	    risk.put("risk_confidence", rm.getConfidence() != null ? rm.getConfidence() : "—");
	} else {
	    risk.put("position_status", "NO_TRADE");
	    risk.put("risk_reason",     "—");
	}

	return risk;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTELLIGENCE SIGNALS (numeric scores for UI gauge)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildSignals(OptionAnalyticsDTO dto) {
	Map<String, Object> sig = new LinkedHashMap<>();

	// Confidence breakdown
	if (dto.getConfidence() != null) {
	    var conf = dto.getConfidence();
	    sig.put("confidence_score", conf.getConfidenceScore() != null ? conf.getConfidenceScore() : 0);
	    sig.put("confidence_level", conf.getConfidenceLevel() != null ? conf.getConfidenceLevel() : "LOW");
	    if (conf.getBreakdown() != null) {
		sig.put("breakdown", conf.getBreakdown());
	    }
	}

	// Dealer positioning
	DealerInventoryModelDTO inv = resolveInventory(dto);
	if (inv != null) {
	    sig.put("dealer_inventory",    inv.getDealerInventory()   != null ? inv.getDealerInventory()   : "—");
	    sig.put("hedging_behavior",    inv.getHedgingBehavior()   != null ? inv.getHedgingBehavior()   : "—");
	    sig.put("volatility_regime",   inv.getVolatilityRegime()  != null ? inv.getVolatilityRegime()  : "—");
	    sig.put("gamma_squeeze_risk",  inv.getGammaSqueeze()      != null ? inv.getGammaSqueeze()      : "—");
	}

	// Premium
	if (dto.getPremiumIntelligence() != null) {
	    sig.put("premium_activity",  dto.getPremiumIntelligence().getPremiumActivity() != null
		    ? dto.getPremiumIntelligence().getPremiumActivity() : "—");
	}

	// Compression
	CompressionDTO comp = resolveCompression(dto);
	if (comp != null) {
	    sig.put("compression",      Boolean.TRUE.equals(comp.getCompressionDetected()));
	    sig.put("breakout_signal",  comp.getBreakoutSignal() != null ? comp.getBreakoutSignal() : "—");
	    sig.put("wall_spread_pct",  comp.getWallSpreadPct()  != null ? comp.getWallSpreadPct()  : 0.0);
	    sig.put("flip_distance_pct",comp.getFlipDistancePct()!= null ? comp.getFlipDistancePct(): 0.0);
	}

	// Probability
	buildProbability(dto, sig);

	return sig;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REASONS LIST
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> buildReasons(OptionAnalyticsDTO dto) {
	List<String> reasons = new ArrayList<>();

	CompressionDTO comp = resolveCompression(dto);
	if (comp != null && Boolean.TRUE.equals(comp.getCompressionDetected())) {
	    reasons.add("📦 Compression detected — breakout imminent");
	}

	GammaDTO gamma = resolveGamma(dto);
	if (gamma != null && gamma.getGammaFlip() != null && gamma.getGammaFlip() > 0) {
	    reasons.add("⚡ Gamma flip at " + gamma.getGammaFlip().intValue() + " — directional trigger zone");
	}

	if (dto.getPremiumIntelligence() != null
		&& "HIGH".equalsIgnoreCase(dto.getPremiumIntelligence().getPremiumActivity())) {
	    reasons.add("💰 High premium concentration at ATM — institutional activity");
	}

	MarketContextDTO ctx = dto.getMarketContext();
	if (ctx != null && ctx.getExpiry() != null) {
	    int dte = ctx.getExpiry().getDaysToExpiry() != null ? ctx.getExpiry().getDaysToExpiry() : 99;
	    if (dte <= 1) {
		reasons.add("🗓️ Expiry tomorrow — accelerated gamma/theta moves expected");
	    } else if (dte <= 3) {
		reasons.add("🗓️ " + dte + " days to expiry — elevated time decay");
	    }
	}

	DealerInventoryModelDTO inv = resolveInventory(dto);
	if (inv != null) {
	    if ("LONG_GAMMA".equalsIgnoreCase(inv.getDealerInventory())) {
		reasons.add("🏦 Dealers long gamma — expect mean reversion / range behaviour");
	    } else if ("SHORT_GAMMA".equalsIgnoreCase(inv.getDealerInventory())) {
		reasons.add("🏦 Dealers short gamma — expect trending / expansion move");
	    }
	}

	if (dto.getVolatilityContext() != null && dto.getVolatilityContext().getPcr() != null) {
	    Double pcr = dto.getVolatilityContext().getPcr().getPcr();
	    if (pcr != null) {
		if (pcr > 1.2) reasons.add("📊 PCR " + pcr + " — bullish put writing, support below");
		else if (pcr < 0.7) reasons.add("📊 PCR " + pcr + " — bearish call writing, resistance above");
	    }
	}

	if (comp != null && "IMMINENT_BREAKOUT".equalsIgnoreCase(comp.getBreakoutSignal())) {
	    reasons.add("🚀 Compression breakout signal triggered — prepare entry");
	}

	if (dto.getExecutionTiming() != null && dto.getExecutionTiming().getReason() != null) {
	    reasons.add("⏱️ " + dto.getExecutionTiming().getReason());
	}

	if (reasons.isEmpty()) {
	    reasons.add("System monitoring — no high-conviction signal yet");
	}

	return reasons;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEAR INDEX
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildFearIndex(OptionAnalyticsDTO dto) {
	Map<String, Object> fi = new LinkedHashMap<>();
	if (dto.getCompleteDecision() != null
		&& dto.getCompleteDecision().getFearIndexAnalysis() != null) {
	    var fa = dto.getCompleteDecision().getFearIndexAnalysis();
	    fi.put("score",               fa.getCurrentFearIndex() != null ? fa.getCurrentFearIndex() : 0.0);
	    fi.put("zone",                fa.getZone()             != null ? fa.getZone()             : "—");
	    fi.put("recommended_action",  fa.getRecommendedAction()!= null ? fa.getRecommendedAction(): "—");
	}
	return fi;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRADING CARD (session guidance)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildTradingCard(OptionAnalyticsDTO dto) {
	if (dto.getCompleteDecision() != null
		&& dto.getCompleteDecision().getTradingCard() != null) {
	    var tc = dto.getCompleteDecision().getTradingCard();
	    Map<String, Object> card = new LinkedHashMap<>();
	    card.put("session_1", tc.getSession1() != null ? tc.getSession1() : "—");
	    card.put("session_3", tc.getSession3() != null ? tc.getSession3() : "—");
	    card.put("btst",      tc.getBtst()     != null ? tc.getBtst()     : "NO BTST");
	    return card;
	}
	return Map.of("session_1", "—", "session_3", "—", "btst", "NO BTST");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXECUTION PLAN (state machine + trigger engine)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildExecutionPlan(OptionAnalyticsDTO dto, String symbol) {
	Map<String, Object> plan = new LinkedHashMap<>();
	try {
	    TriggerResult trigger  = triggerEngine.evaluate(dto);
	    String currentPhase    = stateMachine.getPhase(symbol).name();
	    String phaseDesc       = stateMachine.getPhase(symbol).describe();

	    plan.put("long_trigger",          trigger.getLongTrigger());
	    plan.put("short_trigger",         trigger.getShortTrigger());
	    plan.put("long_option",           trigger.getLongOption());
	    plan.put("short_option",          trigger.getShortOption());
	    plan.put("confirmation_needed",   trigger.isConfirmationNeeded());
	    plan.put("expected_move",         trigger.getExpectedMove());
	    plan.put("trade_type",            trigger.getTradeType());
	    plan.put("state",                 currentPhase);
	    plan.put("state_description",     phaseDesc);
	    plan.put("breakout_distance_pct", Math.round(trigger.getBreakoutDistancePct() * 100.0) / 100.0);
	    plan.put("trigger_direction",     trigger.getTriggerDirection());
	    plan.put("trigger_reason",        trigger.getTriggerReason());
	    plan.put("should_execute",        trigger.isShouldExecute());
	    plan.put("passed_conditions",     trigger.getPassedConditions());
	    plan.put("failed_conditions",     trigger.getFailedConditions());

	    // Regime narrative
	    DealerInventoryModelDTO inv = resolveInventory(dto);
	    String dealer = inv != null && inv.getDealerInventory() != null   ? inv.getDealerInventory()  : "";
	    String vol    = inv != null && inv.getVolatilityRegime()  != null ? inv.getVolatilityRegime() : "";
	    plan.put("regime_edge", buildRegimeEdge(dealer, vol));

	    // Playbook
	    plan.put("playbook", buildPlaybook(trigger.getLongTrigger(), trigger.getShortTrigger()));

	} catch (Exception e) {
	    log.error("❌ buildExecutionPlan error: {}", e.getMessage());
	    plan.put("state", "UNKNOWN");
	    plan.put("error", e.getMessage());
	}
	return plan;
    }

    private String buildRegimeEdge(String dealer, String vol) {
	if ("SHORT_GAMMA".equalsIgnoreCase(dealer) && vol.toUpperCase().contains("EXPANDING"))
	    return "SHORT_GAMMA + EXPANDING_VOL → Trending breakout expected";
	if ("SHORT_GAMMA".equalsIgnoreCase(dealer))
	    return "SHORT_GAMMA → Market will trend after breakout";
	if ("LONG_GAMMA".equalsIgnoreCase(dealer))
	    return "LONG_GAMMA → Range / mean reversion expected";
	return dealer + "_" + vol;
    }

    private Map<String, Object> buildPlaybook(int longTrigger, int shortTrigger) {
	Map<String, Object> pb = new LinkedHashMap<>();
	pb.put("above_" + longTrigger,  "BUY CE — breakout up confirmed");
	pb.put("below_" + shortTrigger, "BUY PE — breakdown confirmed");
	pb.put("between",               "NO TRADE — wait in " + shortTrigger + "–" + longTrigger + " range");
	pb.put("after_entry",           "Trail SL at 10% profit, target RR 1:2");
	pb.put("expiry_day",            "Take quick profit, don't hold overnight");
	return pb;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveRegime(OptionAnalyticsDTO dto) {
	DealerInventoryModelDTO inv = resolveInventory(dto);
	if (inv != null && inv.getDealerInventory() != null) {
	    return inv.getDealerInventory() + "_" + (inv.getVolatilityRegime() != null ? inv.getVolatilityRegime() : "RANGE");
	}
	if (dto.getHistoricalContext() != null && dto.getHistoricalContext().getRegime() != null)
	    return dto.getHistoricalContext().getRegime();
	return "UNKNOWN";
    }

    private String resolveStrategy(OptionAnalyticsDTO dto) {
	if (dto.getTradeSignal() != null && dto.getTradeSignal().getStrategy() != null)
	    return dto.getTradeSignal().getStrategy();
	if (dto.getAutoTradeDecision() != null && dto.getAutoTradeDecision().getAutoTradeAction() != null
		&& dto.getAutoTradeDecision().getAutoTradeAction().getAction() != null)
	    return dto.getAutoTradeDecision().getAutoTradeAction().getAction();
	return "UNKNOWN";
    }

    private String resolveAction(OptionAnalyticsDTO dto) {
	if (dto.getAutoTradeDecision() != null && dto.getAutoTradeDecision().getAutoTradeAction() != null
		&& dto.getAutoTradeDecision().getAutoTradeAction().getAction() != null)
	    return dto.getAutoTradeDecision().getAutoTradeAction().getAction();
	return "WAIT";
    }

    private String resolveDirection(OptionAnalyticsDTO dto) {
	if (dto.getAutoTradeDecision() != null && dto.getAutoTradeDecision().getAutoTradeAction() != null
		&& dto.getAutoTradeDecision().getAutoTradeAction().getDirection() != null)
	    return dto.getAutoTradeDecision().getAutoTradeAction().getDirection();
	return "—";
    }

    private int resolveConfidence(OptionAnalyticsDTO dto) {
	if (dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null)
	    return dto.getConfidence().getConfidenceScore();
	return 0;
    }

    private String resolveSession(OptionAnalyticsDTO dto) {
	MarketContextDTO ctx = dto.getMarketContext();
	if (ctx != null && ctx.getSession() != null && ctx.getSession().getSession() != null)
	    return ctx.getSession().getSession();
	return "—";
    }

    private Map<String, Object> resolveExpiry(OptionAnalyticsDTO dto) {
	Map<String, Object> expiry = new LinkedHashMap<>();
	MarketContextDTO ctx = dto.getMarketContext();
	if (ctx != null && ctx.getExpiry() != null) {
	    ExpiryDTO e = ctx.getExpiry();
	    expiry.put("date",            e.getNearestExpiry() != null ? e.getNearestExpiry() : "—");
	    expiry.put("days_to_expiry",  e.getDaysToExpiry()  != null ? e.getDaysToExpiry()  : 0);
	    expiry.put("phase",           e.getPhase()         != null ? e.getPhase()          : "—");
	}
	return expiry;
    }

    private GammaDTO resolveGamma(OptionAnalyticsDTO dto) {
	if (dto.getDealerPositioning() != null)
	    return dto.getDealerPositioning().getGamma();
	return null;
    }

    private DealerInventoryModelDTO resolveInventory(OptionAnalyticsDTO dto) {
	if (dto.getDealerPositioning() != null)
	    return dto.getDealerPositioning().getDealerInventoryModel();
	return null;
    }

    private CompressionDTO resolveCompression(OptionAnalyticsDTO dto) {
	if (dto.getMarketStructure() != null)
	    return dto.getMarketStructure().getCompression();
	return null;
    }

    private void buildProbability(OptionAnalyticsDTO dto, Map<String, Object> sig) {
	try {
	    if (dto.getMarketStructure() != null && dto.getMarketStructure().getProbabilityModel() != null) {
		sig.put("probability_model", dto.getMarketStructure().getProbabilityModel());
	    }
	} catch (Exception ignored) {}
    }

    /** Format "22500/23000" → "Break 23000 → BUY CE | Break 22500 → BUY PE" */
    private String formatTrigger(String raw) {
	if (raw == null || raw.isBlank()) return "Waiting for breakout trigger";
	if (raw.contains("/")) {
	    String[] parts = raw.split("/");
	    if (parts.length == 2) {
		try {
		    int put  = Integer.parseInt(parts[0].trim());
		    int call = Integer.parseInt(parts[1].trim());
		    return "Break " + call + " → BUY CE  |  Break " + put + " → BUY PE";
		} catch (NumberFormatException ignored) {}
	    }
	}
	return raw;
    }
}
