package com.pietrader.journal;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;

/** Facade over JournalService for use inside execution layer. Impl: JournalFacadeServiceImpl */
public interface IJournalFacadeService {
    void record(Trade trade, OptionAnalyticsDTO dto);
    void recordExit(String symbol, double exitPrice, String exitReason, double pnl);
}
