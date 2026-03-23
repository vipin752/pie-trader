package com.pietrader.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.state.TradeState;
import com.pietrader.state.TradeStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PIE TRADER — TradeStateManagerTest
 *
 * Verifies that every Redis write uses the CONTRACT-specified key format.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeStateManager — Redis Key Contract Compliance")
class TradeStateManagerTest {

    @Mock StringRedisTemplate  redis;
    @Mock ValueOperations<String, String> ops;

    private TradeStateManager manager;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        manager = new TradeStateManager(redis, mapper);
        ReflectionTestUtils.setField(manager, "capital",      200000.0);
        ReflectionTestUtils.setField(manager, "maxDailyLoss", 2000.0);
    }

    // ── Lock key format ───────────────────────────────────────────────────────

    @Test @DisplayName("lock() writes to trade:lock:NIFTY")
    void lockKeyFormat() {
        manager.lock("NIFTY", Duration.ofMinutes(5));
        verify(ops).set(eq("trade:lock:NIFTY"), eq("1"), any(Duration.class));
    }

    @Test @DisplayName("isLocked() reads trade:lock:NIFTY")
    void isLockedKeyFormat() {
        when(redis.hasKey("trade:lock:NIFTY")).thenReturn(false);
        manager.isLocked("NIFTY");
        verify(redis).hasKey("trade:lock:NIFTY");
    }

    @Test @DisplayName("unlock() deletes trade:lock:NIFTY")
    void unlockKeyFormat() {
        manager.unlock("NIFTY");
        verify(redis).delete("trade:lock:NIFTY");
    }

    // ── Position key format ───────────────────────────────────────────────────

    @Test @DisplayName("savePosition() writes to trade:position:NIFTY")
    void positionKeyFormat() throws Exception {
        TradeState state = new TradeState();
        state.setSymbol("NIFTY");
        state.setStrike("NIFTY24APR23200CE");
        manager.savePosition("NIFTY", state);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).set(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo("trade:position:NIFTY");
    }

    // ── Trade count key format ────────────────────────────────────────────────

    @Test @DisplayName("incrementTradeCount() uses trade:tradesToday:NIFTY")
    void tradeCountKeyFormat() {
        when(ops.increment("trade:tradesToday:NIFTY")).thenReturn(1L);
        manager.incrementTradeCount("NIFTY");
        verify(ops).increment("trade:tradesToday:NIFTY");
    }

    // ── Daily PnL key format ──────────────────────────────────────────────────

    @Test @DisplayName("updateDailyPnl() uses trade:dailyPnL:NIFTY")
    void pnlKeyFormat() {
        when(ops.get("trade:dailyPnL:NIFTY")).thenReturn(null);
        manager.updateDailyPnl("NIFTY", 500.0);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops, atLeastOnce()).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertThat(keyCaptor.getAllValues()).contains("trade:dailyPnL:NIFTY");
    }

    // ── Cooldown key format ───────────────────────────────────────────────────

    @Test @DisplayName("setCooldown() uses trade:cooldown:NIFTY")
    void cooldownKeyFormat() {
        manager.setCooldown("NIFTY", Duration.ofMinutes(5));
        verify(ops).set(eq("trade:cooldown:NIFTY"), eq("1"), any(Duration.class));
    }

    // ── Last trade time ───────────────────────────────────────────────────────

    @Test @DisplayName("setLastTradeTime() uses trade:lastTradeTime:NIFTY")
    void lastTradeTimeKeyFormat() {
        manager.setLastTradeTime("NIFTY");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("trade:lastTradeTime:NIFTY");
    }

    // ── Global keys ───────────────────────────────────────────────────────────

    @Test @DisplayName("setMode() writes to trade:mode (no symbol suffix)")
    void modeGlobalKey() {
        manager.setMode("AUTO");
        verify(ops).set("trade:mode", "AUTO");
    }

    @Test @DisplayName("setCapital() writes to trade:capital")
    void capitalGlobalKey() {
        manager.setCapital(500000.0);
        verify(ops).set("trade:capital", "500000.0");
    }

    @Test @DisplayName("setMaxTrades() writes to trade:maxTrades")
    void maxTradesGlobalKey() {
        manager.setMaxTrades(3);
        verify(ops).set("trade:maxTrades", "3");
    }

    // ── Case normalisation ────────────────────────────────────────────────────

    @Test @DisplayName("Lowercase symbol is normalised to uppercase in key")
    void lowercaseNormalized() {
        manager.lock("nifty", Duration.ofMinutes(1));
        verify(ops).set(eq("trade:lock:NIFTY"), any(), any(Duration.class));
    }

    // ── EOD reset cleans all contract keys ───────────────────────────────────

    @Test @DisplayName("resetDailyState() deletes all per-symbol contract keys")
    void eodResetDeletesAllKeys() {
        manager.resetDailyState("NIFTY");
        verify(redis).delete("trade:tradesToday:NIFTY");
        verify(redis).delete("trade:dailyPnL:NIFTY");
        verify(redis).delete("trade:maxLossBreach:NIFTY");
        verify(redis).delete("trade:cooldown:NIFTY");
        verify(redis).delete("trade:lock:NIFTY");
        verify(redis).delete("trade:currentLossPercent:NIFTY");
    }
}
