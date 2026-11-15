from kafka import KafkaProducer
import json

class KafkaSignalProducer:

    def __init__(self):
        self.producer = KafkaProducer(
            bootstrap_servers='localhost:9092',
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
    
    def send_signal(self, data: dict):
        print("🚀 Sending to Kafka:", data.get("trade_signal", {}))
        self.producer.send("pie.analytics.results", data)
        self.producer.flush()