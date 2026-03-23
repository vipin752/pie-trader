package com.pietrader.execution;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.stats.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * PIE TRADER — TradingOrchestrator
 *
 * @Primary bean — replaces ExecutionServiceImpl for all execution wiring.
 *
 * Full 10-step flow:
 *   AnalyticsResult → ModeManager → ExecutionGate → RiskManager →
 *   PositionSizingEngine → OrderManager → PositionManager →
 *   ExitEngine → JournalFacade → StatsService
 *
 * STATE MACHINE INTEGRATION:
 *   After Step 7 (order placed):       stateMachine.onTradePlaced()   → MANAGE
 *   In squareOff (position closed):    stateMachine.onPositionClosed() → NO_TRADE
 *
 * WHY:
 *   TradeStateMachine.getPhase("NIFTY") drives the execution_plan.state
 *   in the trade card. Without these hooks, the state stays at EXECUTE
 *   forever even after a trade is placed and closed.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class TradingOrchestrator implements ExecutionService {

    private final ModeManager          modeManager;
    private final ExecutionGate        executionGate;
    private final RiskManagerFacade    riskManager;
    private final PositionSizingEngine sizingEngine;
    private final OrderManager         orderManager;
    private final PositionManager      positionManager;
    private final ExitEngine           exitEngine;
    private final TradeJournalFacade   journalService;
    private final StatsService         statsService;
    private final TradeStateMachine    stateMachine;    // NEW — state machine hook

    // ── ExecutionService.execute (called by AnalyticsConsumer) ───────────────

    @Override
    public void execute(OptionAnalyticsDTO dto) {
        if (dto == null) { log.warn("⛔ Null DTO — skipping"); return; }

        String symbol     = resolveSymbol(dto);
        int    confidence = resolveConfidence(dto);

        log.info("🔔 Orchestrator → symbol={} confidence={} phase={}",
                symbol, confidence, stateMachine.getPhase(symbol));

        try {
            // Step 1: Mode guard
            if (modeManager.isManualMode()) {
                log.info("⛔ [{}] Manual mode — skipping", symbol);
                return;
            }

            // Step 2: ExecutionGate
            // Gate passes only when BreakoutTriggerEngine set execution_ready=true
            // and action=EXECUTE in the mutated DTO (done in AnalyticsConsumer).
            ExecutionGate.GateResult gate = executionGate.check(dto);
            if (!gate.isAllowed()) {
                log.info("⛔ [{}] Gate BLOCKED: {}", symbol, gate.getReason());
                return;
            }

            // Step 3: Risk
            RiskManagerFacade.RiskResult risk = riskManager.check(symbol, confidence);
            if (!risk.isAllowed()) {
                log.warn("⛔ [{}] Risk BLOCKED: {}", symbol, risk.getReason());
                return;
            }

            // Step 4: Strike / direction (set by BreakoutTriggerEngine in DTO)
            AutoTradeActionDTO action = resolveAction(dto);
            if (action == null || action.getOption() == null || action.getDirection() == null) {
                log.error("❌ [{}] No strike/direction in DTO", symbol);
                return;
            }

            // Step 5: Position sizing
            int lots = sizingEngine.calculate(symbol, confidence, dto);
            if (lots <= 0) {
                log.warn("⛔ [{}] 0 lots — skipping", symbol);
                return;
            }

            log.info("✅ [{}] ALL GATES PASSED → strike={} dir={} lots={} mode={}",
                    symbol, action.getOption(), action.getDirection(), lots, modeManager.currentMode());

            // Step 6: Place order
            Trade trade = orderManager.execute(OrderManager.TradeRequest.builder()
                    .tradeId(UUID.randomUUID().toString())
                    .symbol(symbol)
                    .strike(action.getOption())
                    .direction(action.getDirection())
                    .lots(lots)
                    .confidence(confidence)
                    .mode(modeManager.currentMode())
                    .dto(dto)
                    .requestedAt(Instant.now().toEpochMilli())
                    .build());

            if (trade == null || !trade.isSuccess()) {
                log.error("❌ [{}] Order failed: {}",
                        symbol, trade != null ? trade.getFailureReason() : "null");
                return;
            }

            // Step 7: Save position
            positionManager.openPosition(trade, dto);

            // Step 7b: Transition state machine → MANAGE
            // Must happen AFTER openPosition so the position exists in Redis
            stateMachine.onTradePlaced(symbol);

            // Step 8: Register exit engine
            exitEngine.register(trade, dto);

            // Step 9: Journal
            journalService.record(trade, dto);

            // Step 10: Stats
            try {
                statsService.onTradeClosed(symbol, 0.0, "OPENED");
            } catch (Exception e) {
                log.error("❌ StatsService error: {}", e.getMessage());
            }

            log.info("🎯 [{}] TRADE COMPLETE → orderId={} phase={}",
                    symbol, trade.getOrderId(), stateMachine.getPhase(symbol));

        } catch (Exception e) {
            log.error("❌ [{}] Orchestrator error: {}", symbol, e.getMessage(), e);
        }

        // Feed ExitEngine with latest market tick
        exitEngine.onAnalyticsTick(symbol, dto);
    }

    // ── ExecutionService.forceExecute (called by ExecutionController) ─────────

    @Override
    public OrderResponse forceExecute(String symbol, String strike, String direction) {
        log.warn("⚡ FORCE EXECUTE → {} {} {}", symbol, strike, direction);

        Trade trade = orderManager.forceExecute(
                symbol.toUpperCase(), strike.toUpperCase(), direction.toUpperCase());

        if (trade.isSuccess()) {
            stateMachine.onTradePlaced(symbol.toUpperCase());   // STATE → MANAGE
        }

        return trade.isSuccess()
                ? OrderResponse.builder()
                .status(modeManager.isPaperMode() ? "PAPER" : "SUCCESS")
                .orderId(trade.getOrderId())
                .symbol(trade.getSymbol())
                .strike(trade.getStrike())
                .direction(trade.getDirection())
                .price(trade.getEntryPrice())
                .quantity(trade.getQuantity())
                .timestamp(System.currentTimeMillis())
                .build()
                : OrderResponse.builder()
                .status("FAILED")
                .errorMessage(trade.getFailureReason())
                .build();
    }

    // ── ExecutionService.squareOff (called by ExecutionController / ExitEngine) ─

    @Override
    public OrderResponse squareOff(String symbol) {
        com.pietrader.state.TradeState state = positionManager.getPosition(symbol);

        if (state == null || !state.isTradeActive()) {
            return OrderResponse.builder()
                    .status("NO_POSITION")
                    .symbol(symbol)
                    .build();
        }

        positionManager.closePosition(
                symbol,
                state.getCurrentPrice(),
                "MANUAL_SQUAREOFF",
                state.getPnl()
        );

        // Transition state machine → NO_TRADE
        stateMachine.onPositionClosed(symbol);

        return OrderResponse.builder()
                .status("SUCCESS")
                .symbol(symbol)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveSymbol(OptionAnalyticsDTO dto) {
        return dto.getMarketContext() != null && dto.getMarketContext().getSymbol() != null
                ? dto.getMarketContext().getSymbol() : "NIFTY";
    }

    private int resolveConfidence(OptionAnalyticsDTO dto) {
        return dto.getConfidence() != null && dto.getConfidence().getConfidenceScore() != null
                ? dto.getConfidence().getConfidenceScore() : 0;
    }

    private AutoTradeActionDTO resolveAction(OptionAnalyticsDTO dto) {
        return dto.getAutoTradeDecision() != null
                ? dto.getAutoTradeDecision().getAutoTradeAction() : null;
    }
}
