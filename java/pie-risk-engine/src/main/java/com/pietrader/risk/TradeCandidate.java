package com.pietrader.risk;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A candidate order, as it would reach the risk gate right before submission.
 *
 * <p>Either pass {@code riskInr} directly, or pass {@code entryPremium} and
 * {@code stopPremium} and let the engine derive and validate the risk from
 * them (QA Suite B: RISK-007 zero stop distance, RISK-008 stop not below
 * entry, RISK-009 negative premium). When both are given, the derived value
 * wins.
 */
public record TradeCandidate(
    @NotBlank String underlying,
    @NotBlank String symbol,
    @Positive int qty,
    @Positive int lotSize,
    Double entryPremium,                 // nullable
    Double stopPremium,                  // nullable
    @PositiveOrZero double riskInr,      // ₹ lost if the stop is hit; used when premiums aren't given
    LocalDate expiry,                    // null if not an option / not applicable
    LocalDateTime now
) {
    public TradeCandidate(String underlying, String symbol, int qty, int lotSize, double riskInr,
                          LocalDate expiry, LocalDateTime now) {
        this(underlying, symbol, qty, lotSize, null, null, riskInr, expiry, now);
    }

    boolean hasPremiums() {
        return entryPremium != null && stopPremium != null;
    }

    double effectiveRisk() {
        return hasPremiums() ? (entryPremium - stopPremium) * qty : riskInr;
    }
}
