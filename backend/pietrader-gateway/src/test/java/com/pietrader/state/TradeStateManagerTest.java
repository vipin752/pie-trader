package com.pietrader.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.redis.RedisKeys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeStateManager — Redis State Tests")
class TradeStateManagerTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @InjectMocks TradeStateManager manager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SYMBOL = "NIFTY";

    @BeforeEach
    void setUp() throws Exception {
        // Inject real ObjectMapper via reflection
        var f = TradeStateManager.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(manager, objectMapper);

        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── TRADE LOCK ────────────────────────────────────────────────────────────

    @Test @DisplayName("isLocked: true when key exists")
    void isLocked_trueWhenKeyExists() {
        when(redis.hasKey(RedisKeys.tradeLock(SYMBOL))).thenReturn(true);
        assertThat(manager.isLocked(SYMBOL)).isTrue();
    }

    @Test @DisplayName("isLocked: false when key absent")
    void isLocked_falseWhenAbsent() {
        when(redis.hasKey(RedisKeys.tradeLock(SYMBOL))).thenReturn(false);
        assertThat(manager.isLocked(SYMBOL)).isFalse();
    }

    @Test @DisplayName("lock: sets key with correct TTL")
    void lock_setsKeyWithTtl() {
        manager.lock(SYMBOL, Duration.ofMinutes(5));
        verify(valueOps).set(eq(RedisKeys.tradeLock(SYMBOL)), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test @DisplayName("unlock: deletes lock key")
    void unlock_deletesKey() {
        manager.unlock(SYMBOL);
        verify(redis).delete(RedisKeys.tradeLock(SYMBOL));
    }

    // ── POSITION ──────────────────────────────────────────────────────────────

    @Test @DisplayName("savePosition: serializes and stores TradeState JSON")
    void savePosition_storesJson() {
        TradeState state = new TradeState();
        state.setSymbol(SYMBOL); state.setTradeActive(true); state.setStrike("23200 PE");
        manager.savePosition(SYMBOL, state);
        verify(valueOps).set(eq(RedisKeys.position(SYMBOL)), argThat(json -> json.contains("23200 PE")));
    }

    @Test @DisplayName("getPosition: deserializes TradeState from Redis")
    void getPosition_deserializesState() throws Exception {
        TradeState state = new TradeState();
        state.setSymbol(SYMBOL); state.setTradeActive(true); state.setStrike("23000 CE");
        when(valueOps.get(RedisKeys.position(SYMBOL))).thenReturn(objectMapper.writeValueAsString(state));

        TradeState result = manager.getPosition(SYMBOL);
        assertThat(result).isNotNull();
        assertThat(result.getStrike()).isEqualTo("23000 CE");
        assertThat(result.isTradeActive()).isTrue();
    }

    @Test @DisplayName("getPosition: returns null when not found")
    void getPosition_nullWhenNotFound() {
        when(valueOps.get(RedisKeys.position(SYMBOL))).thenReturn(null);
        assertThat(manager.getPosition(SYMBOL)).isNull();
    }

    @Test @DisplayName("hasActivePosition: true when tradeActive=true in state")
    void hasActivePosition_trueWhenActive() throws Exception {
        TradeState state = new TradeState();
        state.setTradeActive(true);
        when(valueOps.get(RedisKeys.position(SYMBOL))).thenReturn(objectMapper.writeValueAsString(state));
        assertThat(manager.hasActivePosition(SYMBOL)).isTrue();
    }

    @Test @DisplayName("hasActivePosition: false when no position in Redis")
    void hasActivePosition_falseWhenNoState() {
        when(valueOps.get(RedisKeys.position(SYMBOL))).thenReturn(null);
        assertThat(manager.hasActivePosition(SYMBOL)).isFalse();
    }

    @Test @DisplayName("clearPosition: deletes position key")
    void clearPosition_deletesKey() {
        manager.clearPosition(SYMBOL);
        verify(redis).delete(RedisKeys.position(SYMBOL));
    }

    // ── DAILY TRADE COUNT ─────────────────────────────────────────────────────

    @Test @DisplayName("incrementTradeCount: increments and sets 24h TTL")
    void incrementTradeCount_incrementsWithTtl() {
        when(valueOps.increment(RedisKeys.dailyTradeCount(SYMBOL))).thenReturn(1L);
        long count = manager.incrementTradeCount(SYMBOL);
        assertThat(count).isEqualTo(1L);
        verify(redis).expire(eq(RedisKeys.dailyTradeCount(SYMBOL)), eq(Duration.ofHours(24)));
    }

    @Test @DisplayName("getDailyTradeCount: returns 0 when key absent")
    void getDailyTradeCount_zeroWhenAbsent() {
        when(valueOps.get(RedisKeys.dailyTradeCount(SYMBOL))).thenReturn(null);
        assertThat(manager.getDailyTradeCount(SYMBOL)).isEqualTo(0L);
    }

    @Test @DisplayName("getDailyTradeCount: returns value from Redis")
    void getDailyTradeCount_returnsValue() {
        when(valueOps.get(RedisKeys.dailyTradeCount(SYMBOL))).thenReturn("3");
        assertThat(manager.getDailyTradeCount(SYMBOL)).isEqualTo(3L);
    }

    // ── DAILY PNL ─────────────────────────────────────────────────────────────

    @Test @DisplayName("updateDailyPnl: accumulates PnL correctly")
    void updateDailyPnl_accumulates() {
        when(valueOps.get(RedisKeys.dailyPnl(SYMBOL))).thenReturn("1000.0");
        manager.updateDailyPnl(SYMBOL, 500.0);
        verify(valueOps).set(eq(RedisKeys.dailyPnl(SYMBOL)), eq("1500.0"), eq(Duration.ofHours(24)));
    }

    @Test @DisplayName("updateDailyPnl: starts from 0 when no prior PnL")
    void updateDailyPnl_startsFromZero() {
        when(valueOps.get(RedisKeys.dailyPnl(SYMBOL))).thenReturn(null);
        manager.updateDailyPnl(SYMBOL, -800.0);
        verify(valueOps).set(eq(RedisKeys.dailyPnl(SYMBOL)), eq("-800.0"), eq(Duration.ofHours(24)));
    }

    @Test @DisplayName("getDailyPnl: returns 0.0 when key absent")
    void getDailyPnl_zeroWhenAbsent() {
        when(valueOps.get(RedisKeys.dailyPnl(SYMBOL))).thenReturn(null);
        assertThat(manager.getDailyPnl(SYMBOL)).isEqualTo(0.0);
    }

    // ── MAX LOSS BREACH ───────────────────────────────────────────────────────

    @Test @DisplayName("setMaxLossBreach: sets flag with 24h TTL")
    void setMaxLossBreach_setsFlag() {
        manager.setMaxLossBreach(SYMBOL);
        verify(valueOps).set(eq(RedisKeys.maxLossBreach(SYMBOL)), eq("1"), eq(Duration.ofHours(24)));
    }

    @Test @DisplayName("isMaxLossBreached: true when flag set")
    void isMaxLossBreached_trueWhenSet() {
        when(redis.hasKey(RedisKeys.maxLossBreach(SYMBOL))).thenReturn(true);
        assertThat(manager.isMaxLossBreached(SYMBOL)).isTrue();
    }

    // ── COOLDOWN ──────────────────────────────────────────────────────────────

    @Test @DisplayName("setCooldown: sets key with given TTL")
    void setCooldown_setsWithTtl() {
        manager.setCooldown(SYMBOL, Duration.ofMinutes(5));
        verify(valueOps).set(eq(RedisKeys.cooldown(SYMBOL)), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test @DisplayName("isInCooldown: true when key present")
    void isInCooldown_trueWhenPresent() {
        when(redis.hasKey(RedisKeys.cooldown(SYMBOL))).thenReturn(true);
        assertThat(manager.isInCooldown(SYMBOL)).isTrue();
    }

    // ── EOD RESET ─────────────────────────────────────────────────────────────

    @Test @DisplayName("resetDailyState: deletes all 5 daily keys")
    void resetDailyState_deletesAllKeys() {
        manager.resetDailyState(SYMBOL);
        verify(redis).delete(RedisKeys.dailyTradeCount(SYMBOL));
        verify(redis).delete(RedisKeys.dailyPnl(SYMBOL));
        verify(redis).delete(RedisKeys.maxLossBreach(SYMBOL));
        verify(redis).delete(RedisKeys.cooldown(SYMBOL));
        verify(redis).delete(RedisKeys.tradeLock(SYMBOL));
    }

    // ── SIGNAL CACHE ──────────────────────────────────────────────────────────

    @Test @DisplayName("cacheSignal: stores signal with 5min TTL")
    void cacheSignal_storesWithTtl() {
        manager.cacheSignal(SYMBOL, "{\"action\":\"EXECUTE\"}");
        verify(valueOps).set(eq(RedisKeys.latestSignal(SYMBOL)),
                eq("{\"action\":\"EXECUTE\"}"), eq(Duration.ofMinutes(5)));
    }

    @Test @DisplayName("getCachedSignal: returns stored signal")
    void getCachedSignal_returnsValue() {
        when(valueOps.get(RedisKeys.latestSignal(SYMBOL))).thenReturn("{\"action\":\"WAIT\"}");
        assertThat(manager.getCachedSignal(SYMBOL)).isEqualTo("{\"action\":\"WAIT\"}");
    }
}
