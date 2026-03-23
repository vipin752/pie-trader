from kafka import KafkaProducer
import json
import logging
from config.settings import Settings

logger = logging.getLogger(__name__)


class KafkaProducerService:
    """
    Generic Kafka producer. Sends JSON messages to any topic.
    """

    def __init__(self):
        self.producer = KafkaProducer(
            bootstrap_servers=Settings.KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            retries=3,
            linger_ms=5,
            batch_size=32768,
            acks="all",
        )
        logger.info("✅ KafkaProducerService ready")

    def send(self, topic: str, value: dict, key: str = None):
        try:
            self.producer.send(topic, value=value, key=key)
        except Exception as e:
            logger.error(f"❌ Kafka send failed → topic={topic}: {e}")

    def flush(self):
        self.producer.flush()

    def close(self):
        self.producer.flush()
        self.producer.close()


# ── TICK PRODUCER (backward compat) ────────────────────────────────────────────
class KafkaTickProducer(KafkaProducerService):
    def send_tick(self, tick: dict):
        self.send(Settings.KAFKA_TICK_TOPIC, tick, key=tick.get("symbol"))
