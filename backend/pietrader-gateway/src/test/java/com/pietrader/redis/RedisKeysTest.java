package com.pietrader.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — RedisKeysTest
 *
 * Validates that ALL Redis key strings match the DATA CONTRACT exactly.
 * If this test breaks, a key was changed that must NOT change.
 */
@DisplayName("Redis Keys — Data Contract Validation")
class RedisKeysTest {

    // ── Global keys ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("CONTRACT: trade:mode")
    void globalMode() {
        assertThat(RedisKeys.tradeState("TRADE_MODE")).isEqualTo("trade:mode");
    }

    @Test
    @DisplayName("CONTRACT: trade:capital")
    void globalCapital() {
        assertThat(RedisKeys.position("trade_lock")).isEqualTo("trade:capital");
    }

    @Test
    @DisplayName("CONTRACT: trade:maxRiskPerTrade")
    void globalMaxRisk() {
        assertThat(RedisKeys.position("TRADE_MAX_RISK_PER_TRADE")).isEqualTo("trade:maxRiskPerTrade");
    }

    @Test
    @DisplayName("CONTRACT: trade:maxDailyLoss")
    void globalMaxDailyLoss() {
        assertThat(RedisKeys.position("TRADE_MAX_DAILY_LOSS")).isEqualTo("trade:maxDailyLoss");
    }

    @Test
    @DisplayName("CONTRACT: trade:maxTrades")
    void globalMaxTrades() {
        assertThat(RedisKeys.position("TRADE_MAX_TRADES")).isEqualTo("trade:maxTrades");
    }

    // ── Per-symbol keys ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"})
    @DisplayName("CONTRACT: trade:position:{symbol}")
    void positionKey(String symbol) {
        assertThat(RedisKeys.position(symbol))
            .isEqualTo("trade:position:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:lock:{symbol}")
    void lockKey(String symbol) {
        assertThat(RedisKeys.tradeLock(symbol))
            .isEqualTo("trade:lock:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:cooldown:{symbol}")
    void cooldownKey(String symbol) {
        assertThat(RedisKeys.cooldown(symbol))
            .isEqualTo("trade:cooldown:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:dailyPnL:{symbol}")
    void dailyPnlKey(String symbol) {
        assertThat(RedisKeys.dailyPnl(symbol))
            .isEqualTo("trade:dailyPnL:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:currentLossPercent:{symbol}")
    void lossPercentKey(String symbol) {
        assertThat(RedisKeys.maxLossBreach("currentLossPercent"))
            .isEqualTo("trade:currentLossPercent:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:tradesToday:{symbol}")
    void tradeCountKey(String symbol) {
        assertThat(RedisKeys.dailyTradeCount(symbol))
            .isEqualTo("trade:tradesToday:" + symbol.toUpperCase());
    }

    @ParameterizedTest(name = "symbol={0}")
    @ValueSource(strings = {"NIFTY", "BANKNIFTY"})
    @DisplayName("CONTRACT: trade:lastTradeTime:{symbol}")
    void lastTradeTimeKey(String symbol) {
        assertThat(RedisKeys.lastTradeTime(symbol))
            .isEqualTo("trade:lastTradeTime:" + symbol.toUpperCase());
    }

    // ── Case-normalisation ────────────────────────────────────────────────────

    @Test
    @DisplayName("Lowercase symbol is uppercased in key")
    void lowercaseNormalized() {
        assertThat(RedisKeys.position("nifty"))
            .isEqualTo("trade:position:NIFTY");
        assertThat(RedisKeys.tradeLock("banknifty"))
            .isEqualTo("trade:lock:BANKNIFTY");
    }

    // ── No key contains old snake_case format ─────────────────────────────────

    @Test
    @DisplayName("No key uses old snake_case format (trade_lock:)")
    void noSnakeCaseFormat() {
        assertThat(RedisKeys.tradeLock("NIFTY")).doesNotContain("trade_lock");
        assertThat(RedisKeys.position("NIFTY")).doesNotStartWith("position:");
        assertThat(RedisKeys.dailyTradeCount("NIFTY")).doesNotContain("daily_trade_count");
        assertThat(RedisKeys.dailyPnl("NIFTY")).doesNotContain("daily_pnl:");
        assertThat(RedisKeys.cooldown("NIFTY")).doesNotStartWith("cooldown:");
    }
}
