package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;

/** 13-rule exit engine. Impl: ExitEngineServiceImpl */
public interface IExitEngineService {
    void register(Trade trade, OptionAnalyticsDTO dto);
    void onAnalyticsTick(String symbol, OptionAnalyticsDTO dto);
}
