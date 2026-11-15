package com.pietrader.execution;

/** Facade over RiskManager for use inside execution layer. Impl: RiskManagerFacadeImpl */
public interface IRiskFacadeService {

    RiskResult check(String symbol, int confidence);
    void       onTradeExecuted(String symbol);
    void       onTradeClosed(String symbol, double pnl);

    class RiskResult {
        private final boolean allowed;
        private final String  reason;
        private RiskResult(boolean a, String r) { this.allowed = a; this.reason = r; }
        public static RiskResult allow()              { return new RiskResult(true,  "OK"); }
        public static RiskResult block(String reason) { return new RiskResult(false, reason); }
        public boolean isAllowed() { return allowed; }
        public String  getReason() { return reason; }
    }
}
