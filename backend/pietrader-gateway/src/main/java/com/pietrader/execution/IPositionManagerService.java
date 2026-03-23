package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.state.TradeState;

import java.util.List;

/** Manages live position lifecycle in Redis. Impl: PositionManagerServiceImpl */
public interface IPositionManagerService {
    void         openPosition(Trade trade, OptionAnalyticsDTO dto);
    void         updateSl(String symbol, double newSl);
    void         updatePnl(String symbol, double currentPrice);
    void         closePosition(String symbol, double exitPrice, String exitReason, double pnl);
    TradeState   getPosition(String symbol);
    boolean      hasActivePosition(String symbol);
    List<String> getActiveSymbols();
}
