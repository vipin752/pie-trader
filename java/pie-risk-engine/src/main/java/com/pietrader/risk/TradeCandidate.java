package com.pietrader.risk;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A candidate order, as it would reach the risk gate right before submission. */
public record TradeCandidate(
    @NotBlank String underlying,
    @NotBlank String symbol,
    @Positive int qty,
    @Positive int lotSize,
    @Positive double riskInr,          // ₹ lost if the planned stop is hit, for this qty
    LocalDate expiry,                  // null if not an option / not applicable
    LocalDateTime now
) {}
