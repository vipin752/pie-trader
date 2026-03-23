package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import lombok.Builder;
import lombok.Getter;

/** Places paper or live orders. Impl: OrderManagerServiceImpl */
public interface IOrderManagerService {
    Trade execute(OrderRequest request);
    Trade forceExecute(String symbol, String strike, String direction);

    @Getter @Builder
    class OrderRequest {
        private String             tradeId;
        private String             symbol;
        private String             strike;
        private String             direction;
        private int                lots;
        private int                confidence;
        private TradeMode          mode;
        private OptionAnalyticsDTO dto;
        private long               requestedAt;
    }
}
