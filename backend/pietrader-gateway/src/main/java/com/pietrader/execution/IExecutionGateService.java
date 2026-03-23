package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;

/** Pre-trade 5-gate validation. Impl: ExecutionGateServiceImpl */
public interface IExecutionGateService {
    GateResult check(OptionAnalyticsDTO dto);

    class GateResult {
        private final boolean allowed;
        private final String  reason;
        private GateResult(boolean a, String r) { this.allowed = a; this.reason = r; }
        public static GateResult allow()              { return new GateResult(true,  "OK"); }
        public static GateResult block(String reason) { return new GateResult(false, reason); }
        public boolean isAllowed() { return allowed; }
        public String  getReason() { return reason; }
    }
}
