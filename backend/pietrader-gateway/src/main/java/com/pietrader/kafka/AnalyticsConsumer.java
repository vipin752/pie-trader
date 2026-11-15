package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.BreakoutTriggerEngine;
import com.pietrader.execution.ExecutionService;
import com.pietrader.execution.TradeStateMachine;
import com.pietrader.execution.model.TriggerResult;
import com.pietrader.service.TradeSignalService;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * PIE TRADER — AnalyticsConsumer (FINAL)
 *
 * Consumes pie.analytics.results from Python intelligence engine.
 * Six independent steps — each in its own try-catch:
 *
 *   1. DESERIALIZE        JSON → OptionAnalyticsDTO
 *   2. BREAKOUT TRIGGER   BreakoutTriggerEngine.evaluate(dto)
 *                           • Determines TradePhase (PREPARE/READY/EXECUTE)
 *                           • If EXECUTE: MUTATES dto (execution_ready=true, action=EXECUTE)
 *                           • Updates Redis trade:phase:SYMBOL
 *   3. CACHE SIGNAL       stateManager.cacheSignal() with enriched JSON
 *                           → feeds /api/trade-card  (was returning NO_SIGNAL before)
 *   4. RECORD TIMING      system:last_decision_time in Redis
 *                           → feeds /api/system/health last_decision_time
 *   5. SAVE TO DB         TradeSignalService.process(dto)
 *   6. EXECUTE            executionService.execute(enriched dto)
 *                           → ExecutionGate passes when BreakoutTriggerEngine set execution_ready=true
 *
 * Why mutation approach:
 *   Python sets execution_ready=false until it sees a confirmed breakout.
 *   Java's BreakoutTriggerEngine detects spot price breaking support/resistance
 *   and overrides execution_ready=true + action=EXECUTE in the DTO.
 *   This lets TradingOrchestrator's ExecutionGate pass without any gate logic changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final ExecutionService       executionService;
    private final TradeSignalService     tradeSignalService;
    private final TradeStateManager      stateManager;
    private final StringRedisTemplate    redis;
    private final ObjectMapper           objectMapper;        // JacksonConfig @Primary
    private final BreakoutTriggerEngine  breakoutTriggerEngine;
    private final TradeStateMachine      stateMachine;

    private static final String LAST_DECISION_KEY = "system:last_decision_time";

    @KafkaListener(topics = "pie.analytics.results", groupId = "pietrader-group")
    public void consume(String message) {

	log.info("📥 Kafka received ({} chars)", message.length());

	OptionAnalyticsDTO dto = null;

	// ── STEP 1: DESERIALIZE ────────────────────────────────────────────
	try {
	    dto = objectMapper.readValue(message, OptionAnalyticsDTO.class);

	    log.info("✅ Deserialized:" +
			    " symbol={} spot={} atm={} | dealer={} gammaFlip={}" +
			    " | fearIndex={} zone={} | action={} strike={} confidence={}",
		    dto.getMarketContext()  != null ? dto.getMarketContext().getSymbol() : "NULL",
		    dto.getMarketContext()  != null ? dto.getMarketContext().getSpot()   : "NULL",
		    dto.getMarketContext()  != null ? dto.getMarketContext().getAtm()    : "NULL",
		    dto.getDealerPositioning() != null &&
			    dto.getDealerPositioning().getDealerInventoryModel() != null
			    ? dto.getDealerPositioning().getDealerInventoryModel().getDealerInventory() : "NULL",
		    dto.getDealerPositioning() != null &&
			    dto.getDealerPositioning().getDealerInventoryModel() != null
			    ? dto.getDealerPositioning().getDealerInventoryModel().getGammaFlip() : "NULL",
		    dto.getCompleteDecision() != null &&
			    dto.getCompleteDecision().getFearIndexAnalysis() != null
			    ? dto.getCompleteDecision().getFearIndexAnalysis().getCurrentFearIndex() : "NULL",
		    dto.getCompleteDecision() != null &&
			    dto.getCompleteDecision().getFearIndexAnalysis() != null
			    ? dto.getCompleteDecision().getFearIndexAnalysis().getZone() : "NULL",
		    dto.getAutoTradeDecision() != null &&
			    dto.getAutoTradeDecision().getAutoTradeAction() != null
			    ? dto.getAutoTradeDecision().getAutoTradeAction().getAction() : "NULL",
		    dto.getAutoTradeDecision() != null &&
			    dto.getAutoTradeDecision().getAutoTradeAction() != null
			    ? dto.getAutoTradeDecision().getAutoTradeAction().getOption() : "NULL",
		    dto.getConfidence() != null ? dto.getConfidence().getConfidenceScore() : "NULL"
	    );

	} catch (Exception e) {
	    log.error("❌ Deserialization FAILED: {}", e.getMessage(), e);
	    return;
	}

	// Resolve symbol once
	String symbol = (dto.getMarketContext() != null && dto.getMarketContext().getSymbol() != null)
		? dto.getMarketContext().getSymbol().toUpperCase() : "NIFTY";

	// ── STEP 2: BREAKOUT TRIGGER ENGINE ───────────────────────────────
	// Evaluates spot vs support/resistance + confirmation signals.
	// MUTATES dto if breakout confirmed (execution_ready=true, action=EXECUTE).
	// Updates Redis trade:phase:SYMBOL.
	TriggerResult trigger = null;
	try {
	    trigger = breakoutTriggerEngine.evaluate(dto);
	    log.info("🎯 [{}] Trigger → phase={} execute={} direction={} reason={}",
		    symbol, trigger.getPhase(), trigger.isShouldExecute(),
		    trigger.getTriggerDirection(), trigger.getTriggerReason());

	    if (trigger.isShouldExecute()) {
		log.info("🚀 [{}] BREAKOUT CONFIRMED → strike={} dir={}",
			symbol, trigger.getSelectedStrike(), trigger.getSelectedDirection());
	    }

	} catch (Exception e) {
	    log.error("❌ BreakoutTriggerEngine failed (non-fatal): {}", e.getMessage(), e);
	}

	// ── STEP 3: CACHE ENRICHED SIGNAL FOR TRADE CARD ──────────────────
	// Serialize the (potentially mutated) DTO back to JSON so /api/trade-card
	// shows execution_ready=true and state=EXECUTE when a breakout fires.
	try {
	    String enrichedJson = objectMapper.writeValueAsString(dto);
	    stateManager.cacheSignal(symbol, enrichedJson);
	    log.debug("📦 Signal cached for {} ({} chars)", symbol, enrichedJson.length());
	} catch (Exception e) {
	    // Fallback: cache original message if re-serialization fails
	    log.warn("⚠️ Re-serialization failed — caching original: {}", e.getMessage());
	    try {
		stateManager.cacheSignal(symbol, message);
	    } catch (Exception e2) {
		log.error("❌ Signal cache failed: {}", e2.getMessage());
	    }
	}

	// ── STEP 4: RECORD DECISION TIME FOR HEALTH CHECK ─────────────────
	try {
	    redis.opsForValue().set(
		    LAST_DECISION_KEY,
		    String.valueOf(System.currentTimeMillis()),
		    Duration.ofMinutes(5)
	    );
	} catch (Exception e) {
	    log.error("❌ Decision time record failed (non-fatal): {}", e.getMessage());
	}

	// ── STEP 5: SAVE TO DB ─────────────────────────────────────────────
	try {
	    tradeSignalService.process(dto);
	} catch (Exception e) {
	    log.error("❌ DB save failed: {}", e.getMessage(), e);
	}

	// ── STEP 6: EXECUTION ENGINE ───────────────────────────────────────
	// If BreakoutTriggerEngine mutated dto → execution_ready=true + action=EXECUTE
	// → ExecutionGate.check() passes all gates → TradingOrchestrator places order
	// → TradingOrchestrator calls stateMachine.onTradePlaced() after success
	try {
	    executionService.execute(dto);
	} catch (Exception e) {
	    log.error("❌ Execution failed: {}", e.getMessage(), e);
	}
    }
}
