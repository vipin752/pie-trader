package com.pietrader.risk;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** One underlying's trading day: realised P&L, loss streak, last-loss time. */
final class DayLedger {
    LocalDate day;
    double realised = 0.0;
    int consecutiveLosses = 0;
    final Map<String, LocalDateTime> lastLossAt = new HashMap<>();

    void rollIfNewDay(LocalDate today) {
        if (!today.equals(day)) {
            day = today;
            realised = 0.0;
            consecutiveLosses = 0;
            lastLossAt.clear();
        }
    }
}
