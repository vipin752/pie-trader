"""Broker 'Profit & Loss Statement' xlsx (Equity P&L + F&O P&L sheets).

These statements are already FIFO-matched per contract, with exact charges,
but carry no trade dates. Each row is one closed lot group.
"""
from __future__ import annotations

import datetime as dt
import re
from dataclasses import dataclass
from typing import Optional

_IDX = re.compile(r"^(?P<inst>\w+) (?P<und>\S+) (?P<mon>\w{3}) +(?P<day>\d+) (?P<yr>\d{4}) (?P<strike>[\d.]+) (?P<opt>CE|PE)(?: \((?P<side>\w+)\))?")
_ISO = re.compile(r"^(?P<inst>\w+) (?P<und>\S+) (?P<date>\d{4}-\d{2}-\d{2}) (?P<strike>[\d.]+) (?P<opt>CE|PE)")


@dataclass
class ContractPnL:
    symbol: str
    segment: str          # "FO" | "EQ_DELIVERY" | "EQ_INTRADAY"
    instrument: str       # OPTIDX, OPTSTK, BSXOPT, OPTFUT, EQ
    underlying: str
    expiry: Optional[dt.date]
    strike: Optional[float]
    opt: str              # CE | PE | EQ
    side: str             # BT = bought first, ST = sold first
    qty: int
    avg_buy: float
    avg_sell: float
    gross: float
    charges: float
    net: float

    @property
    def expired_worthless(self) -> bool:
        return self.opt in ("CE", "PE") and self.avg_sell == 0


def _num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return 0.0


def _parse_fo_symbol(s: str):
    m = _IDX.match(s)
    if m:
        exp = dt.datetime.strptime(f"{m['day']} {m['mon']} {m['yr']}", "%d %b %Y").date()
        return m["inst"], m["und"], exp, float(m["strike"]), m["opt"], m["side"] or "BT"
    m = _ISO.match(s)
    if m:
        return m["inst"], m["und"], dt.date.fromisoformat(m["date"]), float(m["strike"]), m["opt"], "BT"
    return s.split()[0], s.split()[1] if len(s.split()) > 1 else s, None, None, "FUT", "BT"


def load_pnl_statement(path) -> list[ContractPnL]:
    import warnings

    import openpyxl

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    out: list[ContractPnL] = []
    for ws in wb.worksheets:
        rows = [tuple(r) for r in ws.iter_rows(values_only=True)]
        segment = None
        for r in rows:
            first = (r[0] or "") if r else ""
            if first in ("Delivery PnL",):
                segment = "EQ_DELIVERY"; continue
            if first in ("Intraday PnL",):
                segment = "EQ_INTRADAY"; continue
            if first == "Scrip Symbol":
                hdr = [str(c or "") for c in r]
                if "Company Name" not in hdr:
                    segment = "FO"
                net_i = len([c for c in hdr if c]) - 1
                gross_i = hdr.index("Gross PnL")
                charge_cols = range(gross_i + 1, net_i)
                continue
            if segment is None or not first or first == "Total" or not isinstance(r[1] if segment == "FO" else r[2], (int, float)):
                continue
            if segment == "FO":
                inst, und, exp, strike, opt, side = _parse_fo_symbol(first)
                qty, ab, asl = r[1], r[2], r[4]
            else:
                inst, und, exp, strike, opt, side = "EQ", first, None, None, "EQ", "BT"
                qty, ab, asl = r[2], r[3], r[5]
            out.append(ContractPnL(
                first, segment, inst, und, exp, strike, opt, side, int(_num(qty)), _num(ab), _num(asl),
                _num(r[gross_i]), sum(_num(r[i]) for i in charge_cols), _num(r[net_i]),
            ))
    if not out:
        raise ValueError("No P&L rows found — is this a broker Profit & Loss statement?")
    return out
