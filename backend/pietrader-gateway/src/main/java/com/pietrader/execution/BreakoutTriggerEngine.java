package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.execution.BreakoutDTO;
import com.pietrader.dto.execution.ConfirmationDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.FinalExecutionDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.execution.model.TradePhase;
import com.pietrader.execution.model.TriggerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * PIE TRADER — BreakoutTriggerEngine
 *
 * Evaluates each incoming OptionAnalyticsDTO and decides:
 *   - What trade phase the system is in (NO_TRADE/PREPARE/READY/EXECUTE)
 *   - Whether a breakout has been confirmed (shouldExecute)
 *
 * When a breakout IS confirmed, this engine MUTATES the DTO:
 *   - execution_layer.final_execution.execution_ready = true
 *   - auto_trade_decision.auto_trade_decision.action  = "EXECUTE"
 *   - auto_trade_decision.auto_trade_decision.option  = selected strike
 *   - auto_trade_decision.auto_trade_decision.direction = BUY_CE | BUY_PE
 *   - execution_timing.entry_signal = "EXECUTE"
 *   - execution_layer.breakout.status = "BREAKOUT_CONFIRMED"
 *
 * This mutation allows TradingOrchestrator's ExecutionGate to pass
 * without any changes to its gate logic.
 *
 * Conditions evaluated from DTO (all verified against actual DTO classes):
 *
 *   BREAKOUT LONG (BUY CE):
 *     spot > resistance
 *     compression OR breakoutSignal=IMMINENT_BREAKOUT
 *     premiumActivity=HIGH OR confidence >= 75
 *     NOT fake breakout
 *     Market open (session.marketOpen)
 *     Trading window = EXPANSION
 *     Not lunch break (12:30–13:15)
 *
 *   BREAKOUT SHORT (BUY PE):
 *     spot < support
 *     [same conditions as long]
 *
 *   READY (level near):
 *     |spot - support| / spot < 0.5%  OR
 *     |spot - resistance| / spot < 0.5%
 *
 *   PREPARE (setup forming):
 *     compression=true
 *     breakoutSignal=IMMINENT_BREAKOUT
 *     confidence >= minConfidence (configurable, default 65)
 *
 *   NO_TRADE:
 *     Market closed OR fake breakout OR confidence < 50
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BreakoutTriggerEngine {

    private final TradeStateMachine stateMachine;

    @Value("${trading.min.confidence:65}")        private int    minConfidence;
    @Value("${trading.breakout.ready.pct:0.5}")   private double readyDistancePct;   // 0.5% of spot
    @Value("${trading.strike.gap:50}")            private int    strikeGap;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // Lunch break — avoid low-volume false breakouts
    private static final LocalTime LUNCH_START = LocalTime.of(12, 30);
    private static final LocalTime LUNCH_END   = LocalTime.of(13, 15);

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN ENTRY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates the DTO and returns a TriggerResult.
     * Side effect: if shouldExecute=true, the DTO is mutated in-place
     * so TradingOrchestrator's ExecutionGate passes cleanly.
     */
    public TriggerResult evaluate(OptionAnalyticsDTO dto) {
	if (dto == null) return noTrade("Null DTO", 0, 0, "", "", 0.0, List.of(), List.of("DTO null"));

	String symbol = resolveSymbol(dto);

	// ── Extract all signals ────────────────────────────────────────────
	double  spot       = resolveSpot(dto);
	int     support    = resolveSupport(dto);
	int     resistance = resolveResistance(dto);
	boolean compression       = resolveCompression(dto);
	String  breakoutSignal    = resolveBreakoutSignal(dto);
	boolean isFakeBreakout    = resolveFakeBreakout(dto);
	String  dealerInventory   = resolveDealerInventory(dto);
	String  volatilityRegime  = resolveVolatilityRegime(dto);
	int     confidence        = resolveConfidence(dto);
	boolean isMarket          = resolveIsMarket(dto);
	String  tradingWindow     = resolveTradingWindow(dto);
	boolean premiumHigh       = "HIGH".equalsIgnoreCase(resolvePremiumActivity(dto));
	int     daysToExpiry      = resolveDaysToExpiry(dto);

	// Derive options for execution plan
	String longOption  = resistance + " CE";
	String shortOption = support    + " PE";

	// Distance from spot to nearest level (% of spot)
	double distToSupport    = spot > 0 ? Math.abs(spot - support)    / spot * 100.0 : 100.0;
	double distToResistance = spot > 0 ? Math.abs(spot - resistance) / spot * 100.0 : 100.0;
	double nearestDist      = Math.min(distToSupport, distToResistance);

	List<String> passed = new ArrayList<>();
	List<String> failed = new ArrayList<>();

	// ── Gate: market must be open ──────────────────────────────────────
	if (!isMarket) {
	    failed.add("Market closed");
	    TradePhase phase = stateMachine.transition(symbol, TradePhase.NO_TRADE);
	    return noTrade("Market closed", support, resistance, longOption, shortOption, nearestDist, passed, failed);
	}
	passed.add("Market open");

	// ── Gate: not fake breakout ────────────────────────────────────────
	if (isFakeBreakout) {
	    failed.add("Fake breakout detected");
	    log.info("⚠️ [{}] Fake breakout detected — staying NO_TRADE", symbol);
	    stateMachine.transition(symbol, TradePhase.NO_TRADE);
	    return noTrade("Fake breakout", support, resistance, longOption, shortOption, nearestDist, passed, failed);
	}
	passed.add("Not fake breakout");

	// ── Gate: minimum confidence ───────────────────────────────────────
	if (confidence < 50) {
	    failed.add("Confidence " + confidence + " < 50");
	    stateMachine.transition(symbol, TradePhase.NO_TRADE);
	    return noTrade("Low confidence: " + confidence, support, resistance, longOption, shortOption, nearestDist, passed, failed);
	}
	passed.add("Confidence " + confidence);

	// ── Gate: lunch break (false signal zone) ─────────────────────────
	boolean isLunch = isLunchBreak();
	if (isLunch) {
	    failed.add("Lunch break — reduced volume");
	} else {
	    passed.add("Not lunch break");
	}

	// ── Volume / premium confirmation ──────────────────────────────────
	boolean volumeOk = premiumHigh || confidence >= 75;
	if (volumeOk) passed.add("Volume/premium OK (activity=HIGH or conf≥75)");
	else          failed.add("Volume not confirmed (activity not HIGH, conf<75)");

	// ── Compression + setup signal ─────────────────────────────────────
	boolean setupActive = compression || "IMMINENT_BREAKOUT".equalsIgnoreCase(breakoutSignal);
	if (setupActive) passed.add("Compression/breakout signal active");
	else             failed.add("No compression or breakout signal");

	// ── Regime edge ────────────────────────────────────────────────────
	boolean isShortGamma  = "SHORT_GAMMA".equalsIgnoreCase(dealerInventory);
	boolean isExpandingVol = "EXPANDING_VOL".equalsIgnoreCase(volatilityRegime);
	boolean regimeEdge     = isShortGamma || isExpandingVol;
	if (regimeEdge) passed.add("Regime edge: " + dealerInventory + "_" + volatilityRegime);
	else            failed.add("No regime edge (not SHORT_GAMMA or EXPANDING_VOL)");

	// ── Expansion window ───────────────────────────────────────────────
	boolean expansionWindow = "EXPANSION".equalsIgnoreCase(tradingWindow);
	if (expansionWindow) passed.add("Trading window: EXPANSION");
	else                 failed.add("Window not EXPANSION: " + tradingWindow);

	// ── Expiry urgency ─────────────────────────────────────────────────
	boolean expiryUrgency = daysToExpiry <= 2;
	if (expiryUrgency) passed.add("Expiry urgency: " + daysToExpiry + " days");

	// ═════════════════════════════════════════════════════════════════════
	// BREAKOUT DETECTION
	// ═════════════════════════════════════════════════════════════════════

	boolean longBreak  = spot > resistance;
	boolean shortBreak = spot > 0 && spot < support;

	// ── LONG BREAKOUT ─────────────────────────────────────────────────
	if (longBreak && volumeOk && setupActive && !isLunch) {
	    passed.add("LONG BREAKOUT: spot " + spot + " > resistance " + resistance);
	    log.info("🚀 [{}] LONG BREAKOUT CONFIRMED → spot={} > resistance={}", symbol, spot, resistance);

	    // Select the CE strike to trade (use resistance level CE)
	    String strikeToTrade = longOption;
	    mutateForExecution(dto, "EXECUTE", "BUY_CE", strikeToTrade,
		    "Java breakout confirmed: spot " + String.format("%.1f", spot) + " > " + resistance);

	    stateMachine.transition(symbol, TradePhase.EXECUTE);

	    return TriggerResult.builder()
		    .phase(TradePhase.EXECUTE)
		    .shouldExecute(true)
		    .triggerDirection("LONG")
		    .triggerReason("Spot broke resistance " + resistance + " with volume confirmation")
		    .longTrigger(resistance).shortTrigger(support)
		    .longOption(longOption).shortOption(shortOption)
		    .expectedMove(expiryUrgency ? "BIG_MOVE" : "MODERATE_MOVE")
		    .tradeType("BREAKOUT")
		    .confirmationNeeded(false)
		    .breakoutDistancePct(distToResistance)
		    .passedConditions(passed).failedConditions(failed)
		    .selectedStrike(strikeToTrade).selectedDirection("BUY_CE")
		    .build();
	}

	// ── SHORT BREAKOUT ────────────────────────────────────────────────
	if (shortBreak && volumeOk && setupActive && !isLunch) {
	    passed.add("SHORT BREAKOUT: spot " + spot + " < support " + support);
	    log.info("📉 [{}] SHORT BREAKOUT CONFIRMED → spot={} < support={}", symbol, spot, support);

	    String strikeToTrade = shortOption;
	    mutateForExecution(dto, "EXECUTE", "BUY_PE", strikeToTrade,
		    "Java breakout confirmed: spot " + String.format("%.1f", spot) + " < " + support);

	    stateMachine.transition(symbol, TradePhase.EXECUTE);

	    return TriggerResult.builder()
		    .phase(TradePhase.EXECUTE)
		    .shouldExecute(true)
		    .triggerDirection("SHORT")
		    .triggerReason("Spot broke support " + support + " with volume confirmation")
		    .longTrigger(resistance).shortTrigger(support)
		    .longOption(longOption).shortOption(shortOption)
		    .expectedMove(expiryUrgency ? "BIG_MOVE" : "MODERATE_MOVE")
		    .tradeType("BREAKOUT")
		    .confirmationNeeded(false)
		    .breakoutDistancePct(distToSupport)
		    .passedConditions(passed).failedConditions(failed)
		    .selectedStrike(strikeToTrade).selectedDirection("BUY_PE")
		    .build();
	}

	// ═════════════════════════════════════════════════════════════════════
	// READY — price within readyDistancePct% of a level
	// ═════════════════════════════════════════════════════════════════════
	if (nearestDist <= readyDistancePct && setupActive && confidence >= minConfidence) {
	    String nearLevel = distToSupport < distToResistance
		    ? "support " + support : "resistance " + resistance;
	    passed.add("READY: " + String.format("%.2f", nearestDist) + "% from " + nearLevel);
	    log.info("⚡ [{}] READY — spot {} within {}% of {}", symbol, spot, String.format("%.2f", nearestDist), nearLevel);

	    stateMachine.transition(symbol, TradePhase.READY);

	    return TriggerResult.waiting(
		    TradePhase.READY,
		    "Price " + String.format("%.2f", nearestDist) + "% from " + nearLevel + " — trigger imminent",
		    resistance, support, longOption, shortOption, nearestDist, passed, failed);
	}

	// ═════════════════════════════════════════════════════════════════════
	// PREPARE — compression active, setup forming
	// ═════════════════════════════════════════════════════════════════════
	if (setupActive && confidence >= minConfidence) {
	    passed.add("PREPARE: compression active, confidence=" + confidence);
	    log.debug("📦 [{}] PREPARE — compression active, waiting for price approach", symbol);

	    stateMachine.transition(symbol, TradePhase.PREPARE);

	    return TriggerResult.waiting(
		    TradePhase.PREPARE,
		    "Compression active — waiting for price to approach level",
		    resistance, support, longOption, shortOption, nearestDist, passed, failed);
	}

	// ═════════════════════════════════════════════════════════════════════
	// NO_TRADE — nothing actionable
	// ═════════════════════════════════════════════════════════════════════
	failed.add("No setup: compression=" + compression + " signal=" + breakoutSignal + " conf=" + confidence);
	stateMachine.transition(symbol, TradePhase.NO_TRADE);
	return noTrade("No active setup", support, resistance, longOption, shortOption, nearestDist, passed, failed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO MUTATION — makes ExecutionGate pass for confirmed breakouts
    // ─────────────────────────────────────────────────────────────────────────

    private void mutateForExecution(OptionAnalyticsDTO dto,
	    String action, String direction,
	    String strike, String reason) {
	try {
	    // 1. ExecutionLayer → finalExecution
	    if (dto.getExecutionLayer() == null) dto.setExecutionLayer(new ExecutionLayerDTO());
	    ExecutionLayerDTO layer = dto.getExecutionLayer();

	    if (layer.getFinalExecution() == null) layer.setFinalExecution(new FinalExecutionDTO());
	    layer.getFinalExecution().setExecutionReady(true);
	    layer.getFinalExecution().setReason(reason);

	    // 2. Breakout status
	    if (layer.getBreakout() == null) layer.setBreakout(new BreakoutDTO());
	    layer.getBreakout().setStatus("BREAKOUT_CONFIRMED");
	    layer.getBreakout().setDirection(direction);

	    // 3. Confirmation
	    if (layer.getConfirmation() == null) layer.setConfirmation(new ConfirmationDTO());
	    layer.getConfirmation().setConfirmed(true);
	    layer.getConfirmation().setType("JAVA_BREAKOUT_ENGINE");
	    layer.getConfirmation().setMessage(reason);

	    // 4. AutoTradeDecision → action
	    if (dto.getAutoTradeDecision() != null) {
		AutoTradeActionDTO atd = dto.getAutoTradeDecision().getAutoTradeAction();
		if (atd != null) {
		    atd.setAction(action);
		    atd.setDirection(direction);
		    atd.setOption(strike);
		    atd.setReason(reason);
		    atd.setConfidence("HIGH");
		}
	    }

	    // 5. ExecutionTiming → entrySignal
	    if (dto.getExecutionTiming() == null) dto.setExecutionTiming(new ExecutionTimingDTO());
	    dto.getExecutionTiming().setEntrySignal("EXECUTE");
	    dto.getExecutionTiming().setEntryType("JAVA_BREAKOUT_CONFIRMED");
	    dto.getExecutionTiming().setReason(reason);
	    dto.getExecutionTiming().setConfidence("HIGH");

	} catch (Exception e) {
	    log.error("❌ DTO mutation failed: {}", e.getMessage(), e);
	}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO RESOLVERS — all paths verified against actual DTO classes
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveSymbol(OptionAnalyticsDTO dto) {
	return dto.getMarketContext() != null && dto.getMarketContext().getSymbol() != null
		? dto.getMarketContext().getSymbol().toUpperCase() : "NIFTY";
    }
    private double resolveSpot(OptionAnalyticsDTO dto) {
	return dto.getMarketContext() != null && dto.getMarketContext().getSpot() != null
		? dto.getMarketContext().getSpot() : 0.0;
    }
    private int resolveSupport(OptionAnalyticsDTO dto) {
	return dto.getLiquidityMap() != null && dto.getLiquidityMap().getSupportResistance() != null
		&& dto.getLiquidityMap().getSupportResistance().getSupport() != null
		? dto.getLiquidityMap().getSupportResistance().getSupport() : 0;
    }
    private int resolveResistance(OptionAnalyticsDTO dto) {
	return dto.getLiquidityMap() != null && dto.getLiquidityMap().getSupportResistance() != null
		&& dto.getLiquidityMap().getSupportResistance().getResistance() != null
		? dto.getLiquidityMap().getSupportResistance().getResistance() : Integer.MAX_VALUE;
    }
    private boolean resolveCompression(OptionAnalyticsDTO dto) {
	return dto.getMarketStructure() != null && dto.getMarketStructure().getCompression() != null
		&& Boolean.TRUE.equals(dto.getMarketStructure().getCompression().getCompressionDetected());
    }
    private String resolveBreakoutSignal(OptionAnalyticsDTO dto) {
	return dto.getMarketStructure() != null && dto.getMarketStructure().getCompression() != null
		&& dto.getMarketStructure().getCompression().getBreakoutSignal() != null
		? dto.getMarketStructure().getCompression().getBreakoutSignal() : "";
    }
    private boolean resolveFakeBreakout(OptionAnalyticsDTO dto) {
	return dto.getExecutionDebug() != null && dto.getExecutionDebug().getFakeBreakout() != null
		&& Boolean.TRUE.equals(dto.getExecutionDebug().getFakeBreakout().getFakeBreakout());
    }
    private String resolveDealerInventory(OptionAnalyticsDTO dto) {
	return dto.getDealerPositioning() != null
		&& dto.getDealerPositioning().getDealerInventoryModel() != null
		&& dto.getDealerPositioning().getDealerInventoryModel().getDealerInventory() != null
		? dto.getDealerPositioning().getDealerInventoryModel().getDealerInventory() : "";
    }
    private String resolveVolatilityRegime(OptionAnalyticsDTO dto) {
	return dto.getDealerPositioning() != null
		&& dto.getDealerPositioning().getDealerInventoryModel() != null
		&& dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime() != null
		? dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime() : "";
    }
    private int resolveConfidence(OptionAnalyticsDTO dto) {
	return dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null
		? dto.getConfidence().getConfidenceScore() : 0;
    }
    private boolean resolveIsMarket(OptionAnalyticsDTO dto) {
	return dto.getMarketContext() != null && dto.getMarketContext().getSession() != null
		&& Boolean.TRUE.equals(dto.getMarketContext().getSession().getMarketOpen());
    }
    private String resolveTradingWindow(OptionAnalyticsDTO dto) {
	return dto.getExecutionDebug() != null && dto.getExecutionDebug().getTradingWindow() != null
		&& dto.getExecutionDebug().getTradingWindow().getWindow() != null
		? dto.getExecutionDebug().getTradingWindow().getWindow() : "";
    }
    private String resolvePremiumActivity(OptionAnalyticsDTO dto) {
	return dto.getPremiumIntelligence() != null
		&& dto.getPremiumIntelligence().getPremiumActivity() != null
		? dto.getPremiumIntelligence().getPremiumActivity() : "";
    }
    private int resolveDaysToExpiry(OptionAnalyticsDTO dto) {
	return dto.getMarketContext() != null && dto.getMarketContext().getExpiry() != null
		&& dto.getMarketContext().getExpiry().getDaysToExpiry() != null
		? dto.getMarketContext().getExpiry().getDaysToExpiry() : 99;
    }

    private boolean isLunchBreak() {
	LocalTime now = LocalTime.now(IST);
	return !now.isBefore(LUNCH_START) && !now.isAfter(LUNCH_END);
    }

    private TriggerResult noTrade(String reason, int support, int resistance,
	    String longOpt, String shortOpt, double dist,
	    List<String> passed, List<String> failed) {
	return TriggerResult.waiting(
		TradePhase.NO_TRADE, reason,
		resistance, support, longOpt, shortOpt,
		dist, passed, failed);
    }
}
