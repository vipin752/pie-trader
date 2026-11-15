"""Mirae Asset (m.Stock) 'Trade History' xlsx export."""
from __future__ import annotations

import datetime as dt

from ..models import Trade
from ._seq import assign_seq_from_trade_id

HEADER = ("Trade Date", "Exchange", "Buy / Sell", "Scrip / Contract", "Qty", "Price", "Trade Id")


def load_mirae_xlsx(path):
    import openpyxl

    ws = openpyxl.load_workbook(path, read_only=True, data_only=True).active
    trades, in_table = [], False
    for row in ws.iter_rows(values_only=True):
        cells = tuple(row[:7])
        if cells == HEADER:
            in_table = True
            continue
        if not in_table or not cells[0] or cells[2] not in ("Buy", "Sell"):
            continue
        trades.append(
            Trade(
                date=dt.datetime.strptime(str(cells[0]), "%d-%m-%Y").date(),
                exchange=str(cells[1]),
                side=str(cells[2]),
                symbol=str(cells[3]).strip(),
                qty=int(float(cells[4])),
                price=float(cells[5]),
                trade_id=str(cells[6]),
            )
        )
    if not in_table:
        raise ValueError("Trade table header not found — is this a Mirae trade history export?")
    return assign_seq_from_trade_id(trades)
