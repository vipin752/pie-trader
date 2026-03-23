package com.pietrader.marketdata.kafka;

import com.pietrader.marketdata.dto.MarketTickDTO;

/** Publishes market ticks to pie.market.ticks. Impl: MarketDataProducerImpl */
public interface IMarketDataProducer {
    void publishTick(MarketTickDTO tick);
}
