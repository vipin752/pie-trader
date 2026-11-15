from __future__ import annotations

from option_intelligence.config.contract_config import get_strike_gap


class ATMEngine:

    def __init__(self):
        pass


    def filter_atm_window(self, spot, strikes, symbol="NIFTY", window=15):

        if not strikes:
            return None, []

        symbol = symbol.upper()

        strike_gap = get_strike_gap(symbol)

        atm = round(spot / strike_gap) * strike_gap

        strike_list = sorted({row["strike"] for row in strikes})

        if atm not in strike_list:
            atm = min(strike_list, key=lambda x: abs(x - spot))

        idx = strike_list.index(atm)

        lo = max(0, idx - window)
        hi = min(len(strike_list) - 1, idx + window)

        atm_window = strike_list[lo:hi + 1]

        filtered = [row for row in strikes if row["strike"] in atm_window]

        return atm, filtered
        