package com.pietrader.state;

import com.pietrader.redis.RedisKeys;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("RedisKeys — Key Pattern Tests")
class RedisKeysTest {

    @Test @DisplayName("tradeLock key pattern")
    void tradeLockKey() { assertThat(RedisKeys.tradeLock("NIFTY")).isEqualTo("trade_lock:NIFTY"); }

    @Test @DisplayName("position key pattern")
    void positionKey() { assertThat(RedisKeys.position("NIFTY")).isEqualTo("position:NIFTY"); }

    @Test @DisplayName("dailyTradeCount key pattern")
    void dailyTradeCountKey() { assertThat(RedisKeys.dailyTradeCount("NIFTY")).isEqualTo("daily_trade_count:NIFTY"); }

    @Test @DisplayName("dailyPnl key pattern")
    void dailyPnlKey() { assertThat(RedisKeys.dailyPnl("NIFTY")).isEqualTo("daily_pnl:NIFTY"); }

    @Test @DisplayName("maxLossBreach key pattern")
    void maxLossBreachKey() { assertThat(RedisKeys.maxLossBreach("NIFTY")).isEqualTo("max_loss_breach:NIFTY"); }

    @Test @DisplayName("cooldown key pattern")
    void cooldownKey() { assertThat(RedisKeys.cooldown("NIFTY")).isEqualTo("cooldown:NIFTY"); }

    @Test @DisplayName("lastTradeTime key pattern")
    void lastTradeTimeKey() { assertThat(RedisKeys.lastTradeTime("NIFTY")).isEqualTo("last_trade_time:NIFTY"); }

    @Test @DisplayName("latestSignal key pattern")
    void latestSignalKey() { assertThat(RedisKeys.latestSignal("NIFTY")).isEqualTo("latest_signal:NIFTY"); }

    @Test @DisplayName("All keys are unique per symbol")
    void allKeysUnique() {
        assertThat(RedisKeys.tradeLock("NIFTY")).isNotEqualTo(RedisKeys.position("NIFTY"));
        assertThat(RedisKeys.position("NIFTY")).isNotEqualTo(RedisKeys.cooldown("NIFTY"));
    }

    @Test @DisplayName("Same key type different symbols are different")
    void differentSymbolsDifferentKeys() {
        assertThat(RedisKeys.tradeLock("NIFTY")).isNotEqualTo(RedisKeys.tradeLock("BANKNIFTY"));
        assertThat(RedisKeys.position("NIFTY")).isNotEqualTo(RedisKeys.position("BANKNIFTY"));
    }
}
