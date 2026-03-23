package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;

/** Calculates lot size from confidence tier + loss streak. Impl: PositionSizingServiceImpl */
public interface IPositionSizingService {
    int calculate(String symbol, int confidence, OptionAnalyticsDTO dto);
}
