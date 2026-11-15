package com.pietrader.execution.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.broker.BrokerAdapter;
import com.pietrader.broker.model.OrderRequest;
import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.execution.ExecutionService;
import com.pietrader.kafka.PositionEventProducer;
import com.pietrader.kafka.TradeEventProducer;
import com.pietrader.risk.RiskCheckResult;
import com.pietrader.risk.RiskManager;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionServiceImpl implements ExecutionService {

    private final BrokerAdapter         brokerAdapter;
    private final TradeStateManager     stateManager;
    private final RiskManager           riskManager;
    private final TradeEventProducer    tradeEventProducer;
    private final PositionEventProducer positionEventProducer;
    private final ObjectMapper          objectMapper;

    @Value("${trading.mode:PAPER}")     private String tradingMode;
    @Value("${trading.lot.size:75}")    private int    lotSize;
    @Value("${trading.cooldown.min:5}") private int    cooldownMinutes;

    @Override
    public void execute(OptionAnalyticsDTO dto) {
        if (dto == null) return;
        try {
            String symbol     = resolveSymbol(dto);
            String action     = resolveActionStr(dto);
            int    confidence = resolveConfidence(dto);

            log.info("📊 Execution check → symbol={} action={} confidence={}", symbol, action, confidence);

            if (!"EXECUTE".equalsIgnoreCase(action)) { log.info("⛔ Gate 1 — action={}", action); return; }
            if (!resolveExecutionReady(dto))          { log.info("⛔ Gate 2 — execution_ready=false → {}", resolveBlockReason(dto)); return; }
            String es = resolveEntrySignal(dto);
            if ("NO_ENTRY".equalsIgnoreCase(es)||es==null) { log.info("⛔ Gate 3 — entry_signal={}", es); return; }
            if (!resolveIsMarket(dto))                { log.info("⛔ Gate 4 — market closed"); return; }
            RiskCheckResult risk = riskManager.check(symbol, confidence);
            if (!risk.isAllowed())                    { log.warn("⛔ Gate 5 RISK → {}", risk.getReason()); return; }

            AutoTradeActionDTO auto = resolveAction(dto);
            String strike    = auto != null ? auto.getOption()    : null;
            String direction = auto != null ? auto.getDirection() : null;
            if (strike == null || direction == null) { log.error("❌ Missing strike/direction"); return; }

            log.info("🚀 ALL GATES PASSED → {} {} {} mode={}", symbol, strike, direction, tradingMode);
            OrderResponse result = placeOrder(symbol, strike, direction);
            postExecute(symbol, strike, direction, confidence, result);

        } catch (Exception e) { log.error("❌ Execution error", e); }
    }

    @Override
    public OrderResponse forceExecute(String symbol, String strike, String direction) {
        log.info("🔧 FORCE EXECUTE → {} {} {}", symbol, strike, direction);
        OrderResponse result = placeOrder(symbol, strike, direction);
        postExecute(symbol, strike, direction, 0, result);
        return result;
    }

    @Override
    public OrderResponse squareOff(String symbol) {
        TradeState state = stateManager.getPosition(symbol);
        if (state == null || !state.isTradeActive())
            return OrderResponse.builder().status("FAILED").errorMessage("No active position").build();

        OrderResponse result = brokerAdapter.squareOff(symbol, state.getStrike(), lotSize);
        if ("SUCCESS".equals(result.getStatus()) || "PAPER".equals(result.getStatus())) {
            stateManager.clearPosition(symbol);
            stateManager.unlock(symbol);
            positionEventProducer.publishClosed(symbol, 0.0);
        }
        return result;
    }

    private OrderResponse placeOrder(String symbol, String strike, String direction) {
        if ("PAPER".equalsIgnoreCase(tradingMode)) {
            return OrderResponse.builder().status("PAPER")
                .orderId("PAPER_"+System.currentTimeMillis())
                .symbol(symbol).strike(strike).direction(direction)
                .price(0.0).quantity(lotSize).timestamp(System.currentTimeMillis()).build();
        }
        return brokerAdapter.placeOrder(OrderRequest.builder()
            .symbol(symbol).strike(strike).direction(direction)
            .quantity(lotSize).orderType("MARKET").productType("INTRADAY").build());
    }

    private void postExecute(String symbol, String strike, String direction,
                              int confidence, OrderResponse result) {
        try {
            if ("SUCCESS".equals(result.getStatus()) || "PAPER".equals(result.getStatus())) {
                TradeState state = new TradeState();
                state.setSymbol(symbol); state.setTradeActive(true);
                state.setStrike(strike); state.setDirection(direction);
                state.setEntryPrice(result.getPrice());
                state.setLastExecutionTime(System.currentTimeMillis());
                stateManager.savePosition(symbol, state);

                stateManager.lock(symbol, Duration.ofMinutes(cooldownMinutes));
                stateManager.setCooldown(symbol, Duration.ofMinutes(cooldownMinutes));
                stateManager.setLastTradeTime(symbol);
                riskManager.onTradeExecuted(symbol);

                tradeEventProducer.publishTradeEvent(result);
                positionEventProducer.publishOpen(symbol, strike, direction,
                    result.getOrderId() != null ? result.getOrderId() : "PAPER");

                log.info("✅ TRADE DONE → orderId={} symbol={} strike={}", result.getOrderId(), symbol, strike);
            } else {
                log.error("❌ Order failed → {}", result.getErrorMessage());
            }
        } catch (Exception e) { log.error("❌ Post-execute error", e); }
    }

    private String resolveSymbol(OptionAnalyticsDTO d) {
        return d.getMarketContext()!=null ? d.getMarketContext().getSymbol() : "NIFTY"; }
    private String resolveActionStr(OptionAnalyticsDTO d) {
        AutoTradeActionDTO a=resolveAction(d); return a!=null?a.getAction():"UNKNOWN"; }
    private AutoTradeActionDTO resolveAction(OptionAnalyticsDTO d) {
        return d.getAutoTradeDecision()!=null ? d.getAutoTradeDecision().getAutoTradeAction() : null; }
    private boolean resolveExecutionReady(OptionAnalyticsDTO d) {
        return d.getExecutionLayer()!=null && d.getExecutionLayer().getFinalExecution()!=null
            && Boolean.TRUE.equals(d.getExecutionLayer().getFinalExecution().getExecutionReady()); }
    private String resolveBlockReason(OptionAnalyticsDTO d) {
        return d.getExecutionLayer()!=null && d.getExecutionLayer().getFinalExecution()!=null
            ? d.getExecutionLayer().getFinalExecution().getReason() : "unknown"; }
    private String resolveEntrySignal(OptionAnalyticsDTO d) {
        return d.getExecutionTiming()!=null ? d.getExecutionTiming().getEntrySignal() : null; }
    private boolean resolveIsMarket(OptionAnalyticsDTO d) {
        return d.getMarketContext()!=null && d.getMarketContext().getSession()!=null
            && Boolean.TRUE.equals(d.getMarketContext().getSession().getMarketOpen()); }
    private int resolveConfidence(OptionAnalyticsDTO d) {
        return d.getConfidence()!=null && d.getConfidence().getConfidenceScore()!=null
            ? d.getConfidence().getConfidenceScore() : 0; }
}
