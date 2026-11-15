package com.pietrader.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.ExecutionService;
import com.pietrader.service.TradeSignalService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsConsumer — Kafka Message Processing Tests")
class AnalyticsConsumerTest {

    @Mock ExecutionService   executionService;
    @Mock TradeSignalService tradeSignalService;

    @InjectMocks AnalyticsConsumer consumer;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        var f = AnalyticsConsumer.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(consumer, mapper);
    }

    // ── DESERIALIZATION ───────────────────────────────────────────────────────

    @Test @DisplayName("Valid EXECUTE signal: saves to DB AND executes (both called)")
    void validExecuteSignal_callsBothDbAndExecution() {
        String json = buildSignalJson("EXECUTE", "23000 PE", 75);
        consumer.consume(json);
        verify(tradeSignalService, times(1)).process(any(OptionAnalyticsDTO.class));
        verify(executionService, times(1)).execute(any(OptionAnalyticsDTO.class));
    }

    @Test @DisplayName("Valid WAIT signal: saves to DB AND passes to execution (gate 1 blocks)")
    void validWaitSignal_callsDbAndExecution() {
        String json = buildSignalJson("WAIT", null, 50);
        consumer.consume(json);
        verify(tradeSignalService, times(1)).process(any(OptionAnalyticsDTO.class));
        verify(executionService, times(1)).execute(any(OptionAnalyticsDTO.class));
    }

    @Test @DisplayName("Invalid JSON: does not call DB or execution")
    void invalidJson_doesNotCallServices() {
        consumer.consume("this is not json {{{");
        verifyNoInteractions(tradeSignalService, executionService);
    }

    @Test @DisplayName("Empty message: does not crash")
    void emptyMessage_doesNotCrash() {
        Assertions.assertDoesNotThrow(() -> consumer.consume("{}"));
    }

    @Test @DisplayName("DB save failure does NOT block execution")
    void dbSaveFailure_doesNotBlockExecution() {
        doThrow(new RuntimeException("DB error")).when(tradeSignalService).process(any());
        String json = buildSignalJson("EXECUTE", "23200 CE", 80);
        consumer.consume(json);
        // DB failed but execution must still be called
        verify(executionService, times(1)).execute(any(OptionAnalyticsDTO.class));
    }

    @Test @DisplayName("Execution failure does NOT affect DB save")
    void executionFailure_doesNotAffectDbSave() {
        doThrow(new RuntimeException("Execution error")).when(executionService).execute(any());
        String json = buildSignalJson("EXECUTE", "23000 PE", 70);
        consumer.consume(json);
        // Execution failed but DB save must still have been called
        verify(tradeSignalService, times(1)).process(any(OptionAnalyticsDTO.class));
    }

    @Test @DisplayName("gamma_flip as float (23150.0) deserializes without error")
    void gammaFlipFloat_deserializesCorrectly() {
        String json = "{"
            + "\"market_context\":{\"symbol\":\"NIFTY\",\"spot\":23114.5,\"atm\":23100},"
            + "\"dealer_positioning\":{"
            + "  \"dealer_inventory_model\":{\"gamma_flip\":23150.0,\"net_gamma\":-58.27}"
            + "},"
            + "\"auto_trade_decision\":{"
            + "  \"auto_trade_decision\":{\"action\":\"WAIT\",\"confidence\":\"MEDIUM\"}"
            + "},"
            + "\"confidence\":{\"confidence_score\":60}"
            + "}";
        consumer.consume(json);
        verify(tradeSignalService, times(1)).process(argThat(dto ->
                dto.getDealerPositioning() != null &&
                dto.getDealerPositioning().getDealerInventoryModel() != null &&
                dto.getDealerPositioning().getDealerInventoryModel().getGammaFlip() != null
        ));
    }

    @Test @DisplayName("rr_target as float (2.0) deserializes without null parent DTO")
    void rrTargetFloat_doesNotNullParent() {
        String json = "{"
            + "\"market_context\":{\"symbol\":\"NIFTY\",\"spot\":23100.0},"
            + "\"adaptive_params\":{\"rr_target\":2.0,\"confidence_threshold\":70},"
            + "\"auto_trade_decision\":{\"auto_trade_decision\":{\"action\":\"WAIT\"}}"
            + "}";
        consumer.consume(json);
        verify(tradeSignalService, times(1)).process(argThat(dto ->
                dto.getAdaptiveParams() != null
        ));
    }

    @Test @DisplayName("Unknown JSON fields are ignored (no deserialization failure)")
    void unknownFields_ignored() {
        String json = "{"
            + "\"market_context\":{\"symbol\":\"NIFTY\",\"unknown_new_field\":\"value123\"},"
            + "\"auto_trade_decision\":{\"auto_trade_decision\":{\"action\":\"WAIT\"}},"
            + "\"confidence\":{\"confidence_score\":65}"
            + "}";
        consumer.consume(json);
        verify(tradeSignalService, times(1)).process(any());
    }

    // ── HELPER ────────────────────────────────────────────────────────────────
    private String buildSignalJson(String action, String option, int confidence) {
        String opt = option != null ? "\"" + option + "\"" : "null";
        return "{"
            + "\"market_context\":{"
            + "  \"symbol\":\"NIFTY\",\"spot\":23114.5,\"atm\":23100,"
            + "  \"session\":{\"session\":\"MARKET\",\"is_market\":true,\"time_ist\":\"10:30:00\"}"
            + "},"
            + "\"auto_trade_decision\":{"
            + "  \"auto_trade_decision\":{"
            + "    \"action\":\"" + action + "\","
            + "    \"option\":" + opt + ","
            + "    \"direction\":\"DOWN\","
            + "    \"confidence\":\"MEDIUM\""
            + "  }"
            + "},"
            + "\"execution_layer\":{"
            + "  \"final_execution\":{\"execution_ready\":true,\"reason\":\"All OK\"}"
            + "},"
            + "\"execution_timing\":{"
            + "  \"entry_signal\":\"ENTER_SHORT\""
            + "},"
            + "\"confidence\":{\"confidence_score\":" + confidence + "}"
            + "}";
    }
}
