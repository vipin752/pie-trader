package com.pietrader.risk;

/**
 * Mirrors the deterministic fields of {@code pie_trader.rules.RuleSet}
 * (the Python side keeps the full research-only rule surface; this is only
 * the subset that must be enforced identically wherever orders are placed).
 */
public record RiskRules(
    double capital,
    double maxDailyLoss,
    double maxLossPerTrade,
    int maxLots,
    int minLots,
    int maxConsecutiveLosses,
    int cooldownMinutes,
    boolean noExpiryDayEntries
) {
    public static RiskRules defaults() {
        // Same numbers as rules.toml's [risk]/[behaviour] defaults as of this
        // commit. Keep these two files in sync by hand until one system owns
        // both — see the module README.
        return new RiskRules(200_000, 20_000, 10_000, 10, 6, 2, 0, true);
    }
}
