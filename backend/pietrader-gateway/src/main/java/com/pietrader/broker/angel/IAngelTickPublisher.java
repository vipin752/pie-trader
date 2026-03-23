package com.pietrader.broker.angel;

/** Publishes binary-parsed ticks to Kafka pie.market.ticks. Impl: AngelTickPublisherImpl */
public interface IAngelTickPublisher {
    void publish(String tickJson);
}
