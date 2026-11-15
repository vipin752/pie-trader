"""Broker interface. PaperBroker is the default; live adapters implement the
same three methods. Nothing here sends a real order unless a live adapter is
constructed explicitly with live=True."""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field
from typing import Callable, Protocol

from ..models import Trade


@dataclass
class Fill:
    symbol: str
    side: str
    qty: int
    price: float
    ts: dt.datetime


class Broker(Protocol):
    def quote(self, symbol: str) -> float: ...
    def place_market(self, symbol: str, side: str, qty: int, ts: dt.datetime) -> Fill: ...
    def fills(self) -> list[Fill]: ...


@dataclass
class PaperBroker:
    """Fills at the model/quoted price plus slippage against you."""
    price_fn: Callable[[str], float]
    slippage: float = 0.005  # 0.5 % of premium, against you
    _fills: list = field(default_factory=list)

    def quote(self, symbol: str) -> float:
        return self.price_fn(symbol)

    def place_market(self, symbol, side, qty, ts) -> Fill:
        p = self.price_fn(symbol)
        p = p * (1 + self.slippage) if side == "Buy" else p * (1 - self.slippage)
        f = Fill(symbol, side, qty, max(0.05, round(p * 20) / 20), ts)
        self._fills.append(f)
        return f

    def fills(self):
        return list(self._fills)


def fills_to_trades(fills: list[Fill], exchange_for=lambda s: "BSEFO" if s.startswith("SENSEX") else "NSEFO") -> list[Trade]:
    """Convert bot fills into the same Trade model the analyser uses, so the
    bot's own trades go through the same mistake detectors and report."""
    out = []
    for i, f in enumerate(fills):
        t = Trade(f.ts.date(), exchange_for(f.symbol), f.side, f.symbol, f.qty, f.price, str(i))
        t.seq = (f.ts.hour * 3600 + f.ts.minute * 60 + f.ts.second) / 86400
        out.append(t)
    return out


class LiveBrokerNotConfigured(RuntimeError):
    pass


def make_broker(name: str, price_fn, live: bool = False, **creds):
    """Factory. 'paper' works everywhere. Real adapters (angel, mstock, zerodha,
    dhan, upstox) are added per broker and read keys from environment
    variables, never from this repo. live=True is required to send real orders."""
    if name == "paper":
        return PaperBroker(price_fn)
    if not live:
        raise LiveBrokerNotConfigured("Refusing to create a real broker without live=True")
    raise LiveBrokerNotConfigured(f"No adapter for '{name}' yet. Tell PIE which broker you use.")
