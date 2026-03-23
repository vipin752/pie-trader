package com.pietrader.risk;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskCheckResult {

    private final boolean allowed;
    private final String  reason;

    public static RiskCheckResult allowed() {
        return new RiskCheckResult(true, "✅ Risk check passed");
    }

    public static RiskCheckResult blocked(String reason) {
        return new RiskCheckResult(false, reason);
    }
}
