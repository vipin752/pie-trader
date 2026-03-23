import time

from option_intelligence.ingestion.data_source_manager import DataSourceManager
from option_intelligence.engine.signal_engine import SignalEngine


class OptionChainService:

    def __init__(self):

        self.manager = DataSourceManager()

        self.engine = SignalEngine()

        self.latest_signal = None

    def start(self):

        while True:

            try:

                data = self.manager.fetch()

                signal = self.engine.analyze(data)

                self.latest_signal = signal

                print(signal)

            except Exception as e:

                print("Error:", e)

            time.sleep(15)
            