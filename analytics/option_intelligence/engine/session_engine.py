from __future__ import annotations

from datetime import datetime, time, timezone, timedelta


class SessionEngine:

    def __init__(self):

        self.IST = timezone(timedelta(hours=5, minutes=30))

        self.MARKET_OPEN = time(9, 15)
        self.MARKET_CLOSE = time(15, 30)


    def classify(self):

        now = datetime.now(self.IST).time()

        if now < self.MARKET_OPEN:
            session = "PRE_MARKET"

        elif now > self.MARKET_CLOSE:
            session = "POST_MARKET"

        elif now <= time(10, 30):
            session = "OPENING_SESSION"

        elif now <= time(13, 30):
            session = "MID_SESSION"

        elif now <= time(14, 30):
            session = "AFTERNOON_SESSION"

        else:
            session = "CLOSING_SESSION"

        return {
            "session": session,
            "time_ist": datetime.now(self.IST).strftime("%H:%M:%S"),
            "is_market": self.MARKET_OPEN <= now <= self.MARKET_CLOSE
        }
        