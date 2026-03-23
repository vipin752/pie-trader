from kafka import KafkaConsumer
import json
import logging
from config.settings import Settings

logger = logging.getLogger(__name__)


class KafkaConsumerService:
    """
    Generic Kafka consumer.
    Usage:
        consumer = KafkaConsumerService("pie.market.ticks")
        consumer.listen(callback)
    """

    def __init__(self, topic: str, group_id: str = None):
        self.topic = topic
        self.group_id = group_id or Settings.KAFKA_CONSUMER_GROUP

        self.consumer = KafkaConsumer(
            topic,
            bootstrap_servers=Settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=self.group_id,
            auto_offset_reset="latest",
            enable_auto_commit=True,
            value_deserializer=lambda m: self._safe_deserialize(m),
        )
        logger.info(f"📡 KafkaConsumer ready → topic={topic} group={self.group_id}")

    def _safe_deserialize(self, msg: bytes):
        try:
            return json.loads(msg.decode("utf-8"))
        except Exception:
            return msg.decode("utf-8", errors="replace")

    def listen(self, callback):
        logger.info(f"👂 Listening on {self.topic}...")
        for message in self.consumer:
            try:
                callback(message.value)
            except Exception as e:
                logger.error(f"❌ Callback error on {self.topic}: {e}")
