package com.pietrader.execution.impl;

import com.pietrader.execution.IRiskFacadeService;
import com.pietrader.risk.IRiskService;
import com.pietrader.risk.RiskCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskManagerFacadeImpl implements IRiskFacadeService {

    private final IRiskService riskManager;

    @Override
    public RiskResult check(String symbol, int confidence) {
        RiskCheckResult r = riskManager.check(symbol, confidence);
        return r.isAllowed() ? RiskResult.allow() : RiskResult.block(r.getReason());
    }

    @Override
    public void onTradeExecuted(String symbol) { riskManager.onTradeExecuted(symbol); }

    @Override
    public void onTradeClosed(String symbol, double pnl) { riskManager.onTradeClosed(symbol, pnl); }
}
