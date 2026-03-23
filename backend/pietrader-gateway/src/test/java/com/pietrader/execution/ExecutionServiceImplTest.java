package com.pietrader.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.broker.BrokerAdapter;
import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.*;
import com.pietrader.dto.decision.*;
import com.pietrader.dto.execution.*;
import com.pietrader.dto.market.*;
import com.pietrader.execution.impl.ExecutionServiceImpl;
import com.pietrader.kafka.PositionEventProducer;
import com.pietrader.kafka.TradeEventProducer;
import com.pietrader.risk.RiskCheckResult;
import com.pietrader.risk.RiskManager;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionServiceImpl — 5 Gate + Order Tests")
class ExecutionServiceImplTest {

    @Mock BrokerAdapter         brokerAdapter;
    @Mock TradeStateManager     stateManager;
    @Mock RiskManager           riskManager;
    @Mock TradeEventProducer    tradeEventProducer;
    @Mock PositionEventProducer positionEventProducer;

    @InjectMocks ExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper",    new ObjectMapper());
        ReflectionTestUtils.setField(service, "tradingMode",     "PAPER");
        ReflectionTestUtils.setField(service, "lotSize",         75);
        ReflectionTestUtils.setField(service, "cooldownMinutes", 5);
    }

    // ── BUILDERS ──────────────────────────────────────────────────────────────

    /** Full signal that should pass ALL gates */
    private OptionAnalyticsDTO fullExecuteDto() {
        MarketContextDTO mkt = new MarketContextDTO();
        mkt.setSymbol("NIFTY"); mkt.setSpot(23114.5); mkt.setAtm(23100);
        SessionDTO session = new SessionDTO();
        session.setIsMarket(true); session.setSession("MARKET");
        mkt.setSession(session);

        AutoTradeActionDTO action = new AutoTradeActionDTO();
        action.setAction("EXECUTE"); action.setOption("23000 PE"); action.setDirection("DOWN");

        AutoTradeDecisionDTO atd = new AutoTradeDecisionDTO();
        atd.setAutoTradeAction(action);

        FinalExecutionDTO fe = new FinalExecutionDTO();
        fe.setExecutionReady(true); fe.setReason("All conditions met");

        ExecutionLayerDTO el = new ExecutionLayerDTO();
        el.setFinalExecution(fe);

        ExecutionTimingDTO et = new ExecutionTimingDTO();
        et.setEntrySignal("ENTER_SHORT");

        ConfidenceDTO conf = new ConfidenceDTO();
        conf.setConfidenceScore(75);

        OptionAnalyticsDTO dto = new OptionAnalyticsDTO();
        dto.setMarketContext(mkt);
        dto.setAutoTradeDecision(atd);
        dto.setExecutionLayer(el);
        dto.setExecutionTiming(et);
        dto.setConfidence(conf);
        return dto;
    }

    private OptionAnalyticsDTO withAction(String action) {
        OptionAnalyticsDTO dto = fullExecuteDto();
        dto.getAutoTradeDecision().getAutoTradeAction().setAction(action);
        return dto;
    }

    private OptionAnalyticsDTO withExecutionReady(boolean ready) {
        OptionAnalyticsDTO dto = fullExecuteDto();
        dto.getExecutionLayer().getFinalExecution().setExecutionReady(ready);
        return dto;
    }

    private OptionAnalyticsDTO withEntrySignal(String signal) {
        OptionAnalyticsDTO dto = fullExecuteDto();
        dto.getExecutionTiming().setEntrySignal(signal);
        return dto;
    }

    private OptionAnalyticsDTO withMarket(boolean isMarket) {
        OptionAnalyticsDTO dto = fullExecuteDto();
        dto.getMarketContext().getSession().setIsMarket(isMarket);
        return dto;
    }

    // ── GATE 1: ACTION ────────────────────────────────────────────────────────

    @Test @DisplayName("Gate 1 BLOCK: action=WAIT → no execution")
    void gate1_actionWait_noExecution() {
        service.execute(withAction("WAIT"));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    @Test @DisplayName("Gate 1 BLOCK: action=PREPARE → no execution")
    void gate1_actionPrepare_noExecution() {
        service.execute(withAction("PREPARE"));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    @Test @DisplayName("Gate 1 BLOCK: null DTO → no crash")
    void gate1_nullDto_noCrash() {
        assertThatCode(() -> service.execute(null)).doesNotThrowAnyException();
        verifyNoInteractions(brokerAdapter);
    }

    // ── GATE 2: EXECUTION READY ───────────────────────────────────────────────

    @Test @DisplayName("Gate 2 BLOCK: execution_ready=false")
    void gate2_executionReadyFalse() {
        service.execute(withExecutionReady(false));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    // ── GATE 3: ENTRY SIGNAL ──────────────────────────────────────────────────

    @Test @DisplayName("Gate 3 BLOCK: entry_signal=NO_ENTRY")
    void gate3_noEntry() {
        service.execute(withEntrySignal("NO_ENTRY"));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    @Test @DisplayName("Gate 3 BLOCK: entry_signal=null")
    void gate3_nullEntrySignal() {
        service.execute(withEntrySignal(null));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    // ── GATE 4: IS_MARKET ─────────────────────────────────────────────────────

    @Test @DisplayName("Gate 4 BLOCK: is_market=false (market closed)")
    void gate4_marketClosed() {
        service.execute(withMarket(false));
        verifyNoInteractions(brokerAdapter, riskManager);
    }

    // ── GATE 5: RISK ──────────────────────────────────────────────────────────

    @Test @DisplayName("Gate 5 BLOCK: risk check blocked")
    void gate5_riskBlocked() {
        when(riskManager.check("NIFTY", 75))
                .thenReturn(RiskCheckResult.blocked("Max loss breached"));
        service.execute(fullExecuteDto());
        verifyNoInteractions(brokerAdapter);
    }

    // ── ALL GATES PASS → PAPER ORDER ─────────────────────────────────────────

    @Test @DisplayName("ALL GATES PASS: places paper order, updates Redis, publishes Kafka")
    void allGatesPass_paperOrder() {
        when(riskManager.check("NIFTY", 75)).thenReturn(RiskCheckResult.allowed());

        service.execute(fullExecuteDto());

        // No real broker call in PAPER mode
        verifyNoInteractions(brokerAdapter);

        // Redis state updated
        verify(stateManager).savePosition(eq("NIFTY"), any(TradeState.class));
        verify(stateManager).lock(eq("NIFTY"), any());
        verify(stateManager).setCooldown(eq("NIFTY"), any());
        verify(stateManager).setLastTradeTime("NIFTY");
        verify(riskManager).onTradeExecuted("NIFTY");

        // Kafka events published
        verify(tradeEventProducer).publishTradeEvent(any());
        verify(positionEventProducer).publishOpen(eq("NIFTY"), eq("23000 PE"), eq("DOWN"), anyString());
    }

    @Test @DisplayName("PAPER order response has PAPER status and orderId starting with PAPER_")
    void paperOrderResponse_hasPaperStatus() {
        OrderResponse resp = service.forceExecute("NIFTY", "23000 PE", "DOWN");
        assertThat(resp.getStatus()).isEqualTo("PAPER");
        assertThat(resp.getOrderId()).startsWith("PAPER_");
        assertThat(resp.getSymbol()).isEqualTo("NIFTY");
        assertThat(resp.getStrike()).isEqualTo("23000 PE");
    }

    // ── FORCE EXECUTE ─────────────────────────────────────────────────────────

    @Test @DisplayName("forceExecute: bypasses all gates, returns PAPER response")
    void forceExecute_bypassesGates() {
        OrderResponse resp = service.forceExecute("BANKNIFTY", "52000 CE", "UP");
        assertThat(resp.getStatus()).isEqualTo("PAPER");
        assertThat(resp.getSymbol()).isEqualTo("BANKNIFTY");
        verify(positionEventProducer).publishOpen(eq("BANKNIFTY"), eq("52000 CE"), eq("UP"), anyString());
    }

    // ── SQUARE OFF ────────────────────────────────────────────────────────────

    @Test @DisplayName("squareOff: FAILED when no active position")
    void squareOff_failedWhenNoPosition() {
        when(stateManager.getPosition("NIFTY")).thenReturn(null);
        OrderResponse resp = service.squareOff("NIFTY");
        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getErrorMessage()).contains("No active position");
    }

    @Test @DisplayName("squareOff: FAILED when position not active")
    void squareOff_failedWhenPositionInactive() {
        TradeState inactive = new TradeState();
        inactive.setTradeActive(false);
        when(stateManager.getPosition("NIFTY")).thenReturn(inactive);
        OrderResponse resp = service.squareOff("NIFTY");
        assertThat(resp.getStatus()).isEqualTo("FAILED");
    }

    @Test @DisplayName("squareOff: clears position + unlocks + publishes CLOSED event")
    void squareOff_clearsStateAndPublishesEvent() {
        TradeState active = new TradeState();
        active.setTradeActive(true); active.setStrike("23000 PE");

        when(stateManager.getPosition("NIFTY")).thenReturn(active);
        when(brokerAdapter.squareOff("NIFTY", "23000 PE", 75))
                .thenReturn(OrderResponse.builder().status("SUCCESS").orderId("SQ001").build());

        OrderResponse resp = service.squareOff("NIFTY");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");

        verify(stateManager).clearPosition("NIFTY");
        verify(stateManager).unlock("NIFTY");
        verify(positionEventProducer).publishClosed(eq("NIFTY"), anyDouble());
    }

    // ── LIVE MODE ─────────────────────────────────────────────────────────────

    @Test @DisplayName("LIVE mode: calls brokerAdapter.placeOrder")
    void liveMode_callsBroker() {
        ReflectionTestUtils.setField(service, "tradingMode", "LIVE");

        when(riskManager.check("NIFTY", 75)).thenReturn(RiskCheckResult.allowed());
        when(brokerAdapter.placeOrder(any()))
                .thenReturn(OrderResponse.builder().status("SUCCESS").orderId("ORD123").price(186.5).quantity(75).build());

        service.execute(fullExecuteDto());

        verify(brokerAdapter).placeOrder(argThat(req ->
                "NIFTY".equals(req.getSymbol()) &&
                "23000 PE".equals(req.getStrike()) &&
                "DOWN".equals(req.getDirection()) &&
                req.getQuantity() == 75
        ));
        verify(positionEventProducer).publishOpen(eq("NIFTY"), any(), any(), eq("ORD123"));
    }

    @Test @DisplayName("LIVE mode: broker failure does not crash, logs error")
    void liveMode_brokerFailure_handled() {
        ReflectionTestUtils.setField(service, "tradingMode", "LIVE");
        when(riskManager.check("NIFTY", 75)).thenReturn(RiskCheckResult.allowed());
        when(brokerAdapter.placeOrder(any()))
                .thenReturn(OrderResponse.builder().status("FAILED").errorMessage("Angel error").build());

        assertThatCode(() -> service.execute(fullExecuteDto())).doesNotThrowAnyException();
        verify(positionEventProducer, never()).publishOpen(any(), any(), any(), any());
    }
}
