"""Option contract selection and a Black–Scholes model for paper trading/backtests.
Live trading must use real broker quotes; the model is only a stand-in."""
from __future__ import annotations

import datetime as dt
import math
from dataclasses import dataclass

EXPIRY_CLOSE = dt.time(15, 30)


@dataclass
class Underlying:
    name: str
    lot: int
    strike_step: int
    expiry_weekday: int   # 0=Mon … 3=Thu (weekly expiry day)
    iv: float             # annualised, for the paper model


UNDERLYINGS = {
    "NIFTY": Underlying("NIFTY", 65, 50, 1, 0.13),
    "SENSEX": Underlying("SENSEX", 20, 100, 3, 0.14),
    "BANKNIFTY": Underlying("BANKNIFTY", 30, 100, 1, 0.15),  # monthly in practice; use broker list live
}


@dataclass(frozen=True)
class OptionContract:
    underlying: str
    expiry: dt.date
    strike: float
    opt: str  # CE | PE

    @property
    def symbol(self) -> str:  # same format as the broker trade history
        return f"{self.underlying} {self.expiry.strftime('%d %b %Y').upper()} {self.strike:g} {self.opt}"


def next_expiries(u: Underlying, today: dt.date, n: int = 3) -> list[dt.date]:
    d = today + dt.timedelta(days=(u.expiry_weekday - today.weekday()) % 7)
    return [d + dt.timedelta(weeks=k) for k in range(n)]


def trading_days_between(a: dt.date, b: dt.date) -> int:
    n, d = 0, a
    while d < b:
        d += dt.timedelta(days=1)
        n += d.weekday() < 5
    return n


def choose_contract(u: Underlying, spot: float, direction: str, now: dt.datetime,
                    max_hold_days: int, itm_steps: int = 0, min_extra_days: int = 1) -> OptionContract:
    """ATM (or N strikes ITM) on the nearest expiry that outlives the ride:
    the last held session is max_hold_days-1 trading days out, and the expiry
    must be at least min_extra_days beyond it, so the position is never held
    on its own expiry day."""
    need = max_hold_days - 1 + min_extra_days
    exp = next(e for e in next_expiries(u, now.date(), 4) if trading_days_between(now.date(), e) >= need)
    atm = round(spot / u.strike_step) * u.strike_step
    opt = "CE" if direction == "up" else "PE"
    strike = atm - itm_steps * u.strike_step if opt == "CE" else atm + itm_steps * u.strike_step
    return OptionContract(u.name, exp, strike, opt)


def _ncdf(x):
    return 0.5 * (1 + math.erf(x / math.sqrt(2)))


def bs_price(spot: float, c: OptionContract, now: dt.datetime, iv: float, r: float = 0.065) -> float:
    t_end = dt.datetime.combine(c.expiry, EXPIRY_CLOSE)
    T = max((t_end - now).total_seconds(), 60) / (365 * 86400)
    K = c.strike
    d1 = (math.log(spot / K) + (r + iv * iv / 2) * T) / (iv * math.sqrt(T))
    d2 = d1 - iv * math.sqrt(T)
    if c.opt == "CE":
        p = spot * _ncdf(d1) - K * math.exp(-r * T) * _ncdf(d2)
    else:
        p = K * math.exp(-r * T) * _ncdf(-d2) - spot * _ncdf(-d1)
    return max(round(p * 20) / 20, 0.05)  # tick 0.05


def size_lots(entry_prem: float, stop_prem: float, lot: int, risk_inr: float, max_lots: int,
              capital: float, max_capital_frac: float) -> int:
    """Lots such that hitting the stop loses at most risk_inr, capped by
    max_lots and by the premium outlay allowed per trade."""
    per_unit = max(entry_prem - stop_prem, 0.05)
    by_risk = int(risk_inr // (per_unit * lot))
    by_cap = int((capital * max_capital_frac) // (entry_prem * lot))
    return max(0, min(by_risk, by_cap, max_lots))
