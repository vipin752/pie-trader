package com.pietrader.service;

import com.pietrader.dto.OptionAnalyticsDTO;

/**
 * Pre-execution guard — checks lock, position, cooldown, risk before allowing trade.
 * Impl: ExecutionGuardServiceImpl
 */
public interface IExecutionGuardService {
    boolean canExecute(OptionAnalyticsDTO dto);
}
