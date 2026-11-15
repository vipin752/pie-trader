from __future__ import annotations

import datetime as dt
import re
from dataclasses import dataclass, field
from typing import Optional

_EXP = re.compile(r"(\d{2} [A-Z]{3} \d{4})")


@dataclass
class Trade:
    date: dt.date
    exchange: str
    side: str  # "Buy" | "Sell"
    symbol: str
    qty: int
    price: float
    trade_id: str = ""
    # Ordering key within a day. Broker statements rarely carry timestamps,
    # so parsers derive it (e.g. from trade-id rank). Larger = later.
    seq: float = 0.0

    @property
    def signed_qty(self) -> int:
        return self.qty if self.side == "Buy" else -self.qty

    @property
    def value(self) -> float:
        return self.qty * self.price


def underlying(symbol: str) -> str:
    return symbol.split()[0].replace("-EQ", "")


def expiry(symbol: str) -> Optional[dt.date]:
    m = _EXP.search(symbol)
    return dt.datetime.strptime(m.group(1), "%d %b %Y").date() if m else None


def option_type(symbol: str) -> str:
    s = symbol.strip()
    return s[-2:] if s.endswith(("CE", "PE")) else "EQ"


def is_option(symbol: str) -> bool:
    return option_type(symbol) in ("CE", "PE")


@dataclass
class Episode:
    """One position on one contract: from flat, back to flat."""

    symbol: str
    direction: str  # "L" long / "S" short
    start_date: dt.date
    start_seq: float
    end_date: Optional[dt.date] = None
    end_seq: float = 0.0
    pnl: float = 0.0
    max_qty: int = 0
    first_qty: int = 0
    entries: int = 0
    adds_below_avg: int = 0  # averaging down (long) / up (short)
    orders: list = field(default_factory=list)
    entry_qty: list = field(default_factory=list)
    entry_pnl: list = field(default_factory=list)
    entry_worse: list = field(default_factory=list)

    @property
    def underlying(self) -> str:
        return underlying(self.symbol)

    @property
    def expiry(self) -> Optional[dt.date]:
        return expiry(self.symbol)

    @property
    def is_open(self) -> bool:
        return self.end_date is None

    @property
    def overnight(self) -> bool:
        return self.end_date is not None and self.end_date != self.start_date

    @property
    def dte_at_entry(self) -> Optional[int]:
        e = self.expiry
        return (e - self.start_date).days if e else None

    @property
    def expiry_day(self) -> bool:
        return self.expiry is not None and self.expiry == self.start_date

    @property
    def win(self) -> bool:
        return self.pnl > 0
