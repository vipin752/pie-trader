package com.pietrader.kafka;

/** Publishes position open/close events to pie.position.events. Impl: PositionEventProducerImpl */
public interface IPositionEventProducer {
    void publishOpen(String symbol, String strike, String direction, String orderId);
    void publishClosed(String symbol, double realisedPnl);
}
