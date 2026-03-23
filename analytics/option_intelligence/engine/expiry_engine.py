from datetime import datetime, date
from typing import List, Dict, Any


class ExpiryEngine:

    def analyze(self, symbol: str, expiry_dates: List[str] = None) -> Dict[str, Any]:

        today = date.today()
        parsed = []

        # =========================
        # PARSE EXPIRY DATES SAFELY
        # =========================
        if expiry_dates:
            for d in expiry_dates:
                for fmt in ("%d-%b-%Y", "%Y-%m-%d", "%d/%m/%Y"):
                    try:
                        parsed.append(datetime.strptime(d, fmt).date())
                        break
                    except ValueError:
                        continue  # ✅ only skip format errors

        # =========================
        # NO VALID EXPIRY
        # =========================
        if not parsed:
            return self._default_response()

        # =========================
        # FILTER FUTURE EXPIRIES
        # =========================
        future_expiries = [x for x in parsed if x >= today]

        if not future_expiries:
            return self._default_response()

        # =========================
        # NEAREST EXPIRY
        # =========================
        nearest = min(future_expiries)

        # ✅ FIX: never negative
        dte = max((nearest - today).days, 0)

        # =========================
        # PHASE CLASSIFICATION
        # =========================
        if dte == 0:
            phase = "EXPIRY_DAY"
        elif dte <= 1:
            phase = "EXPIRY_EVE"
        elif dte <= 5:
            phase = "EXPIRY_WEEK"
        elif dte <= 12:
            phase = "POSITIONING_PHASE"
        else:
            phase = "EARLY_CYCLE"

        return {
            "nearest_expiry": nearest.strftime("%d-%b-%Y"),
            "days_to_expiry": dte,
            "phase": phase,
            "tte_years": max(dte, 1) / 365
        }

    # =========================
    # DEFAULT SAFE RESPONSE
    # =========================
    def _default_response(self) -> Dict[str, Any]:
        return {
            "nearest_expiry": "UNKNOWN",
            "days_to_expiry": 0,   # ✅ FIXED (was -1)
            "phase": "UNKNOWN",
            "tte_years": 0.02
        }
        