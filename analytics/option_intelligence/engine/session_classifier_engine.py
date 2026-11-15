"""
Session Classifier Engine (FINAL - Fully Accurate)
"""

from datetime import datetime, time
from typing import Dict, Any


class SessionClassifierEngine:

    def __init__(self, data: Dict[str, Any]):
        self.data = data
        self.current_time = self._get_time()

    # =====================================================
    # MAIN
    # =====================================================

    def classify_session(self) -> Dict[str, Any]:

        t = self.current_time

        if t < time(9, 15):
            return self._build("pre_market", "morning_breakout")

        elif t < time(10, 30):
            return self._build("morning_breakout", "midday_trap")

        elif t < time(13, 30):
            return self._build("midday_trap", "afternoon_sniper")

        elif t < time(14, 45):
            return self._build("afternoon_sniper", "btst_window")

        elif t < time(15, 30):
            return self._build("btst_window", "post_market")

        else:
            return self._build("post_market", "morning_breakout")  # ✅ FIXED

    # =====================================================
    # HELPERS
    # =====================================================

    def _build(self, current: str, next_s: str) -> Dict[str, Any]:
        return {
            "current_session": current,
            "next_session": next_s
        }

    def _get_time(self):

        try:
            time_str = self.data.get("market_context", {}).get("session", {}).get("time_ist", "16:00:00")
            return datetime.strptime(time_str, "%H:%M:%S").time()
        except:
            return time(16, 0)
        