package com.pietrader.execution;

import com.pietrader.risk.RiskCheckResult;
import com.pietrader.risk.RiskManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskManagerFacade {

    private final RiskManager riskManager;

    public RiskResult check(String symbol, int confidence) {
        RiskCheckResult r = riskManager.check(symbol, confidence);
        return r.isAllowed() ? RiskResult.allow() : RiskResult.block(r.getReason());
    }
    public void onTradeExecuted(String symbol) { riskManager.onTradeExecuted(symbol); }
    public void onTradeClosed(String symbol, double pnl) { riskManager.onTradeClosed(symbol, pnl); }

    @Getter
    public static class RiskResult {
        private final boolean allowed;
        private final String  reason;
        private RiskResult(boolean a, String r) { this.allowed = a; this.reason = r; }
        public static RiskResult allow()              { return new RiskResult(true,  "OK"); }
        public static RiskResult block(String reason) { return new RiskResult(false, reason); }
    }
}
