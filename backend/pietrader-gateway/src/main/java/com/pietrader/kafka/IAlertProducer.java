package com.pietrader.kafka;

/** Publishes alerts to pie.alerts Kafka topic. Impl: AlertProducerImpl */
public interface IAlertProducer {
    void info(String type, String message);
    void warn(String type, String message);
    void critical(String type, String message);
}
