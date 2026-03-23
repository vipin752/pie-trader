package com.pietrader.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.impl.ExecutionServiceImpl;
import com.pietrader.kafka.AnalyticsConsumer;
import com.pietrader.kafka.PositionEventProducer;
import com.pietrader.kafka.TradeEventProducer;
import com.pietrader.risk.RiskManager;
import com.pietrader.service.TradeSignalService;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Full pipeline integration test.
 *
 * Simulates the complete end-to-end flow:
 *
 *   Python JSON published to pie.analytics.results
 *        ↓
 *   AnalyticsConsumer.consume(json)
 *        ↓
 *   TradeSignalService.process(dto)  [DB save — independent]
 *        ↓
 *   ExecutionServiceImpl.execute(dto) [5-gate check]
 *        ↓
 *   Paper order placed
 *        ↓
 *   Redis state saved (position, lock, cooldown)
 *        ↓
 *   pie.trade.events   [Java → audit]
 *   pie.position.events [Java → Python feedback]
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Full Pipeline Integration — Python Signal to Paper Order")
class FullPipelineTest {

    @Mock KafkaTemplate<String,String>        kafkaTemplate;
    @Mock com.pietrader.broker.BrokerAdapter   brokerAdapter;
    @Mock StringRedisTemplate                  redis;
    @Mock ValueOperations<String,String>       valueOps;
    @Mock TradeSignalService                   tradeSignalService;

    private TradeStateManager     stateManager;
    private RiskManager           riskManager;
    private ExecutionServiceImpl  executionService;
    private AnalyticsConsumer     consumer;

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true);

    @BeforeEach
    void setUp() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.hasKey(anyString())).thenReturn(false);
        lenient().when(valueOps.increment(anyString())).thenReturn(1L);
        lenient().when(valueOps.get(anyString())).thenReturn(null);

        stateManager = new TradeStateManager(redis, mapper);
        riskManager  = new RiskManager(stateManager);
        ReflectionTestUtils.setField(riskManager, "maxTradesPerDay", 5);
        ReflectionTestUtils.setField(riskManager, "maxDailyLoss",    2000.0);
        ReflectionTestUtils.setField(riskManager, "minConfidence",   65);

        TradeEventProducer    tradeProducer    = new TradeEventProducer(kafkaTemplate, mapper);
        PositionEventProducer positionProducer = new PositionEventProducer(kafkaTemplate, mapper);

        executionService = new ExecutionServiceImpl(
            brokerAdapter, stateManager, riskManager, tradeProducer, positionProducer, mapper);
        ReflectionTestUtils.setField(executionService, "tradingMode",     "PAPER");
        ReflectionTestUtils.setField(executionService, "lotSize",         75);
        ReflectionTestUtils.setField(executionService, "cooldownMinutes", 5);

        consumer = new AnalyticsConsumer(executionService, tradeSignalService, mapper);
    }

    @Test @DisplayName("TEST 1 — EXECUTE signal triggers paper order + Redis + Kafka events")
    void test1_fullExecuteFlow() {
        consumer.consume(execute("NIFTY","23000 PE","DOWN",75,true,true));

        verify(tradeSignalService).process(any(OptionAnalyticsDTO.class));
        verify(valueOps).set(argThat(k->k.contains("position:NIFTY")), argThat(v->v.contains("23000 PE")), any());
        verify(kafkaTemplate).send(eq("pie.trade.events"), anyString());
        verify(kafkaTemplate).send(eq("pie.position.events"), eq("NIFTY"), argThat(v->v.contains("OPEN")));
        System.out.println("✅ TEST 1 PASS — full pipeline verified");
    }

    @Test @DisplayName("TEST 2 — WAIT signal: saved to DB, no order placed")
    void test2_waitSignal() {
        consumer.consume(wait("NIFTY",60));
        verify(tradeSignalService).process(any());
        verifyNoInteractions(brokerAdapter);
        verify(kafkaTemplate, never()).send(eq("pie.position.events"), anyString(), anyString());
        System.out.println("✅ TEST 2 PASS — WAIT not executed");
    }

    @Test @DisplayName("TEST 3 — Market closed (is_market=false): no execution")
    void test3_marketClosed() {
        consumer.consume(execute("NIFTY","23000 PE","DOWN",75,false,true));
        verifyNoInteractions(brokerAdapter);
        verify(kafkaTemplate, never()).send(eq("pie.position.events"), anyString(), anyString());
        System.out.println("✅ TEST 3 PASS — market closed gate works");
    }

    @Test @DisplayName("TEST 4 — execution_ready=false: gate 2 blocks")
    void test4_executionNotReady() {
        consumer.consume(execute("NIFTY","23000 PE","DOWN",75,true,false));
        verifyNoInteractions(brokerAdapter);
        System.out.println("✅ TEST 4 PASS — execution_ready gate works");
    }

    @Test @DisplayName("TEST 5 — Confidence 50 < 65: risk gate blocks")
    void test5_lowConfidence() {
        consumer.consume(execute("NIFTY","23000 PE","DOWN",50,true,true));
        verifyNoInteractions(brokerAdapter);
        verify(kafkaTemplate, never()).send(eq("pie.position.events"), anyString(), anyString());
        System.out.println("✅ TEST 5 PASS — confidence gate works");
    }

    @Test @DisplayName("TEST 6 — Active position: risk gate blocks duplicate")
    void test6_activePosition_blocksDouble() throws Exception {
        TradeState existing = new TradeState();
        existing.setTradeActive(true); existing.setStrike("23000 PE");
        when(valueOps.get(argThat(k -> k.startsWith("position:NIFTY"))))
            .thenReturn(mapper.writeValueAsString(existing));

        consumer.consume(execute("NIFTY","23000 PE","DOWN",80,true,true));
        verifyNoInteractions(brokerAdapter);
        System.out.println("✅ TEST 6 PASS — active position blocks duplicate");
    }

    @Test @DisplayName("TEST 7 — squareOff: clears Redis + publishes CLOSED event")
    void test7_squareOff() throws Exception {
        TradeState active = new TradeState();
        active.setTradeActive(true); active.setStrike("23000 PE");
        when(valueOps.get(argThat(k -> k.startsWith("position:NIFTY"))))
            .thenReturn(mapper.writeValueAsString(active));
        when(brokerAdapter.squareOff("NIFTY","23000 PE",75))
            .thenReturn(OrderResponse.builder().status("SUCCESS").orderId("SQ1").build());

        OrderResponse resp = executionService.squareOff("NIFTY");
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        verify(redis).delete(argThat(k -> k.startsWith("position:NIFTY")));
        verify(kafkaTemplate).send(eq("pie.position.events"), eq("NIFTY"), argThat(v->v.contains("CLOSED")));
        System.out.println("✅ TEST 7 PASS — square off flow verified");
    }

    @Test @DisplayName("TEST 8 — DB failure does NOT block execution (independent try-catch)")
    void test8_dbFailureDoesNotBlockExecution() {
        doThrow(new RuntimeException("DB down")).when(tradeSignalService).process(any());
        consumer.consume(execute("NIFTY","23000 PE","DOWN",75,true,true));
        verify(kafkaTemplate).send(eq("pie.position.events"), eq("NIFTY"), anyString());
        System.out.println("✅ TEST 8 PASS — DB failure independent of execution");
    }

    @Test @DisplayName("TEST 9 — forceExecute bypasses all gates")
    void test9_forceExecute() {
        OrderResponse resp = executionService.forceExecute("BANKNIFTY","52000 CE","UP");
        assertThat(resp.getStatus()).isEqualTo("PAPER");
        assertThat(resp.getSymbol()).isEqualTo("BANKNIFTY");
        System.out.println("✅ TEST 9 PASS — force execute works");
    }

    @Test @DisplayName("TEST 10 — gamma_flip=23150.0 float deserializes, DTO not null")
    void test10_gammaFlipFloat() {
        String json = "{"
            + "\"market_context\":{\"symbol\":\"NIFTY\",\"spot\":23114.5,"
            +   "\"session\":{\"is_market\":true,\"session\":\"MARKET\"}},"
            + "\"dealer_positioning\":{"
            +   "\"dealer_inventory_model\":{\"gamma_flip\":23150.0,\"net_gamma\":-58.27}"
            + "},"
            + "\"auto_trade_decision\":{\"auto_trade_decision\":{\"action\":\"WAIT\"}},"
            + "\"confidence\":{\"confidence_score\":60}"
            + "}";
        consumer.consume(json);
        verify(tradeSignalService).process(argThat(dto ->
            dto.getDealerPositioning() != null &&
            dto.getDealerPositioning().getDealerInventoryModel() != null &&
            dto.getDealerPositioning().getDealerInventoryModel().getGammaFlip() != null
        ));
        System.out.println("✅ TEST 10 PASS — gamma_flip float handled correctly");
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private String execute(String sym, String opt, String dir, int conf, boolean mkt, boolean ready) {
        return "{\"market_context\":{\"symbol\":\""+sym+"\",\"spot\":23114.5,\"atm\":23100,"
            + "\"session\":{\"is_market\":"+mkt+",\"session\":\"MARKET\"}},"
            + "\"auto_trade_decision\":{\"auto_trade_decision\":{"
            +   "\"action\":\"EXECUTE\",\"option\":\""+opt+"\",\"direction\":\""+dir+"\"}},"
            + "\"execution_layer\":{\"final_execution\":{\"execution_ready\":"+ready+",\"reason\":\"OK\"}},"
            + "\"execution_timing\":{\"entry_signal\":\"ENTER_SHORT\"},"
            + "\"confidence\":{\"confidence_score\":"+conf+"}}";
    }
    private String wait(String sym, int conf) {
        return "{\"market_context\":{\"symbol\":\""+sym+"\",\"spot\":23114.5,"
            + "\"session\":{\"is_market\":true,\"session\":\"MARKET\"}},"
            + "\"auto_trade_decision\":{\"auto_trade_decision\":{\"action\":\"WAIT\"}},"
            + "\"confidence\":{\"confidence_score\":"+conf+"}}";
    }
}
