package com.pietrader.service.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.service.IExecutionGuardService;
import com.pietrader.service.ITradeExecutionStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutionGuardServiceImpl implements IExecutionGuardService {

    private final ITradeExecutionStateService stateService;

    @Override
    public boolean canExecute(OptionAnalyticsDTO dto) {
        if (dto == null) return false;

        try {
            String symbol = dto.getCompleteDecision() != null
                ? dto.getCompleteDecision().getSymbol() : null;
            if (symbol == null && dto.getMarketContext() != null)
                symbol = dto.getMarketContext().getSymbol();
            if (symbol == null) return false;

            String action = dto.getAutoTradeDecision() != null
                && dto.getAutoTradeDecision().getAutoTradeAction() != null
                ? dto.getAutoTradeDecision().getAutoTradeAction().getAction() : null;

            if (!"EXECUTE".equalsIgnoreCase(action))   return false;
            if (stateService.hasActivePosition(symbol)) return false;
            if (stateService.isTradeLocked(symbol))     return false;
            if (stateService.isCooldownActive(symbol))  return false;
            if (!stateService.isRiskAllowed())          return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
