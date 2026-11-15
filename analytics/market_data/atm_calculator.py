from config.constants import STRIKE_GAPS


class ATMCalculator:

    def __init__(self):
        self.last_spot = {s: 0.0 for s in ["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"]}

    def update_spot(self, symbol: str, spot: float):
        self.last_spot[symbol.upper()] = spot

    def get_atm(self, symbol: str) -> int:
        spot = self.last_spot.get(symbol.upper(), 0.0)
        if spot <= 0:
            return 0
        gap = STRIKE_GAPS.get(symbol.upper(), 50)
        return round(spot / gap) * gap

    def get_strikes(self, symbol: str, atm: int, count: int = 10) -> list[int]:
        gap = STRIKE_GAPS.get(symbol.upper(), 50)
        return [atm + i * gap for i in range(-count, count + 1)]
