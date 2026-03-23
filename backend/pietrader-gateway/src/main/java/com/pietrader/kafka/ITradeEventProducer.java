package com.pietrader.kafka;

/** Publishes trade lifecycle events to pie.trade.events. Impl: TradeEventProducerImpl */
public interface ITradeEventProducer {
    void publishTradeEvent(Object payload);
    void publishSquareOff(String symbol);
    void publishAlert(String message);
    void publish(String message);
}
