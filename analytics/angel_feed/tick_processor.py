import time

class TickProcessor:

    def process(self, raw):
        return {
            "symbol": raw.get("symbol"),
            "token": raw.get("token"),
            "ltp": raw.get("ltp"),
            "bid": raw.get("best_bid_price"),
            "ask": raw.get("best_ask_price"),
            "volume": raw.get("volume"),
            "oi": raw.get("open_interest"),
            "timestamp": int(time.time())
        }
        