package com.pietrader.execution;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;

public interface ExecutionService {
    /** Main execution — called by Kafka consumer with full analytics DTO */
    void execute(OptionAnalyticsDTO dto);
    /** Force execute (manual / test) */
    OrderResponse forceExecute(String symbol, String strike, String direction);
    /** Square off active position */
    OrderResponse squareOff(String symbol);
}
