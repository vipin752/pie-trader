package com.pietrader.risk;

import java.util.List;

public record RiskDecision(boolean allowed, List<String> reasons) {
    public static RiskDecision approve() {
        return new RiskDecision(true, List.of());
    }

    public static RiskDecision reject(String... reasons) {
        return new RiskDecision(false, List.of(reasons));
    }
}
