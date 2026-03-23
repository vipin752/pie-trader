import time
import logging
from config.constants import SNAPSHOT_INTERVAL_MINUTES

logger = logging.getLogger(__name__)


class OptionChainSnapshotService:

    def __init__(self, option_chain, postgres_client):
        self.option_chain        = option_chain
        self.postgres            = postgres_client
        self.last_snapshot_time  = 0

    def run_snapshot_if_due(self):
        now = time.time()
        if now - self.last_snapshot_time >= SNAPSHOT_INTERVAL_MINUTES * 60:
            self.take_snapshot()
            self.last_snapshot_time = now

    def take_snapshot(self):
        chain = self.option_chain.get_full_chain()
        rows  = []

        for token, data in chain.items():
            rows.append((
                token,
                data.get("ltp"),
                data.get("bid"),
                data.get("ask"),
                data.get("bid_qty"),
                data.get("ask_qty"),
                data.get("volume"),
                data.get("oi"),
                data.get("timestamp"),
            ))

        if rows:
            try:
                self.postgres.save_option_chain_snapshot(rows)
                logger.info(f"💾 Snapshot saved: {len(rows)} rows")
            except Exception as e:
                logger.error(f"❌ Snapshot save failed: {e}")
