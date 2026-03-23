package com.pietrader.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.execution.PositionManager;
import com.pietrader.state.TradeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — SquareoffConsumerTest
 *
 * Validates contract §9 squareoff flow:
 *   - Single symbol squareoff
 *   - ALL symbols squareoff
 *   - Unknown action ignored
 *   - Malformed JSON handled gracefully
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SquareoffConsumer — Contract §9 Tests")
class SquareoffConsumerTest {

    @Mock PositionManager positionManager;
    @Mock AlertProducer   alertProducer;

    @InjectMocks
    SquareoffConsumer consumer;

    @BeforeEach
    void setUp() {
        // inject real ObjectMapper
        try {
            var field = SquareoffConsumer.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(consumer, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test @DisplayName("SQUAREOFF single symbol → closePosition called once")
    void singleSymbol() {
        when(positionManager.hasActivePosition("NIFTY")).thenReturn(true);
        TradeState state = new TradeState();
        state.setSymbol("NIFTY");
        state.setCurrentPrice(200.0);
        when(positionManager.getPosition("NIFTY")).thenReturn(state);

        consumer.consume("{\"symbol\":\"NIFTY\",\"action\":\"SQUAREOFF\",\"reason\":\"MAX_LOSS\"}");

        verify(positionManager).closePosition(eq("NIFTY"), anyDouble(), contains("MAX_LOSS"), anyDouble());
    }

    @Test @DisplayName("SQUAREOFF ALL → closes every active symbol")
    void squareoffAll() {
        when(positionManager.getActiveSymbols()).thenReturn(List.of("NIFTY", "BANKNIFTY"));
        when(positionManager.hasActivePosition(anyString())).thenReturn(true);
        when(positionManager.getPosition(anyString())).thenReturn(new TradeState());

        consumer.consume("{\"symbol\":\"ALL\",\"action\":\"SQUAREOFF\",\"reason\":\"RISK_BREACH\"}");

        verify(positionManager).closePosition(eq("NIFTY"),     anyDouble(), any(), anyDouble());
        verify(positionManager).closePosition(eq("BANKNIFTY"), anyDouble(), any(), anyDouble());
        verify(alertProducer).critical(eq("SQUAREOFF_ALL"), any());
    }

    @Test @DisplayName("No active position → closePosition NOT called")
    void noActivePosition() {
        when(positionManager.hasActivePosition("NIFTY")).thenReturn(false);
        consumer.consume("{\"symbol\":\"NIFTY\",\"action\":\"SQUAREOFF\",\"reason\":\"TEST\"}");
        verify(positionManager, never()).closePosition(any(), anyDouble(), any(), anyDouble());
    }

    @Test @DisplayName("Unknown action → ignored")
    void unknownAction() {
        consumer.consume("{\"symbol\":\"NIFTY\",\"action\":\"HOLD\",\"reason\":\"TEST\"}");
        verifyNoInteractions(positionManager);
    }

    @Test @DisplayName("Malformed JSON → does not throw")
    void malformedJson() {
        consumer.consume("not-valid-json{{{");
        verifyNoInteractions(positionManager);
    }
}
