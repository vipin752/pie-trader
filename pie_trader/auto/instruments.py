"""Exchange & Contract Intelligence (spec §4).

`UNDERLYINGS` in options.py is a static fallback, clearly labelled as such —
never a source of truth. This module is where a REAL instrument master
belongs: refreshed from a broker's live scrip master or the exchange's own
contract files, with an explicit staleness flag so the rest of the system
can tell paper-fallback data from something that was actually just checked.

No lot size, strike step, expiry weekday or holiday calendar here should be
trusted for a real order until `load_file()` or `refresh_from_broker()` has
been run against a current source.
"""
from __future__ import annotations

import datetime as dt
import json
from dataclasses import dataclass, field, fields
from pathlib import Path
from typing import Optional

from .options import UNDERLYINGS, Underlying

SUPPORTED_INDICES = tuple(UNDERLYINGS)

# Spec §5: correlated-risk control. All five of these track the same broad
# Indian-equity regime most of the time (NIFTY/BANKNIFTY/FINNIFTY/MIDCPNIFTY
# on NSE, SENSEX on BSE) — realised correlation between them is high enough
# that "different index" is not "independent risk". One group by default;
# override only with evidence that two specific indices actually decouple.
CORRELATION_GROUPS = {name: "india_equity_index" for name in SUPPORTED_INDICES}


@dataclass
class ContractSpec(Underlying):
    exchange: str = "NSE"
    segment: str = "OPTIDX"
    tick_size: float = 0.05
    source: str = "static_fallback"    # "static_fallback" | "file" | "broker"
    as_of: Optional[str] = None


def _wrap(u: Underlying, exchange: str, segment: str) -> ContractSpec:
    return ContractSpec(u.name, u.lot, u.strike_step, u.expiry_weekday, u.iv, exchange, segment)


_EXCHANGE = {"SENSEX": ("BSE", "BSXOPT")}


@dataclass
class InstrumentMaster:
    specs: dict = field(default_factory=lambda: {
        name: _wrap(u, *_EXCHANGE.get(name, ("NSE", "OPTIDX"))) for name, u in UNDERLYINGS.items()
    })
    holidays: set = field(default_factory=set)      # dt.date — exchange holidays, both NSE & BSE
    refreshed_at: Optional[dt.datetime] = None

    def get(self, name: str) -> ContractSpec:
        if name not in self.specs:
            raise KeyError(f"{name}: not in the instrument master. Supported: {sorted(self.specs)}")
        return self.specs[name]

    def is_stale(self, max_age_days: int = 7) -> bool:
        """True until a real refresh has happened, and again once it's old."""
        return self.refreshed_at is None or (dt.datetime.now() - self.refreshed_at).days > max_age_days

    def is_trading_day(self, d: dt.date) -> bool:
        return d.weekday() < 5 and d not in self.holidays

    def load_file(self, path) -> None:
        """Load specs + a holiday calendar from a JSON file (see
        instruments.sample.json). The owner refreshes this file from their
        broker's scrip master / NSE-BSE holiday circulars — it is not
        fetched automatically and nothing here invents numbers."""
        data = json.loads(Path(path).read_text())
        known = {f.name for f in fields(ContractSpec)}
        for name, row in data.get("contracts", {}).items():
            bad = set(row) - known
            if bad:
                raise ValueError(f"{name}: unknown instrument fields {sorted(bad)}")
            base = self.specs.get(name) or _wrap(UNDERLYINGS.get(name, Underlying(name, 1, 1, 0, 0.15)), "NSE", "OPTIDX")
            merged = {f.name: getattr(base, f.name) for f in fields(ContractSpec)}
            merged.update(row, name=name, source="file", as_of=data.get("as_of"))
            self.specs[name] = ContractSpec(**merged)
        self.holidays |= {dt.date.fromisoformat(h) for h in data.get("holidays", [])}
        self.refreshed_at = dt.datetime.now()

    def refresh_from_broker(self, broker) -> None:
        raise NotImplementedError(
            "Wire this to your broker's live instrument-master endpoint (e.g. Angel One's "
            "scrip master JSON, updated daily) once that adapter is validated. Until then, "
            "use load_file() with a file you refresh yourself, or the static fallback."
        )
