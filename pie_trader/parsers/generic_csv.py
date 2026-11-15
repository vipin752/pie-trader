"""Broker-neutral CSV.

Columns: date (YYYY-MM-DD), time (HH:MM:SS, optional), exchange, side
(Buy/Sell), symbol, qty, price, trade_id (optional).
If `time` is present it is used for ordering; otherwise trade_id rank is.
"""
from __future__ import annotations

import csv
import datetime as dt

from ..models import Trade
from ._seq import assign_seq_from_trade_id


def load_csv(path):
    trades, has_time = [], True
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            t = Trade(
                date=dt.date.fromisoformat(r["date"]),
                exchange=r.get("exchange", ""),
                side=r["side"].strip().capitalize(),
                symbol=r["symbol"].strip(),
                qty=int(float(r["qty"])),
                price=float(r["price"]),
                trade_id=r.get("trade_id", ""),
            )
            if r.get("time"):
                h, m, s = (int(x) for x in r["time"].split(":"))
                t.seq = (h * 3600 + m * 60 + s) / 86400
            else:
                has_time = False
            trades.append(t)
    if not has_time:
        return assign_seq_from_trade_id(trades)
    trades.sort(key=lambda t: (t.date, t.seq))
    return trades
