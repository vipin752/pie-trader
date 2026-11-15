"""Position accounting: FIFO realised P&L grouped into episodes, plus charges."""
from __future__ import annotations

from collections import defaultdict

from .models import Episode, Trade, is_option


def build_episodes(trades: list[Trade]) -> list[Episode]:
    """Walk trades in time order; every flat→flat cycle on a contract is an Episode.

    Realised P&L is FIFO within the episode. A trade that flips the position
    through zero closes one episode and opens the next.
    """
    lots: dict[str, list[list]] = defaultdict(list)  # [signed_qty, price, entry_idx]
    open_ep: dict[str, Episode] = {}
    done: list[Episode] = []

    for t in sorted(trades, key=lambda t: (t.date, t.seq)):
        q = t.signed_qty
        book = lots[t.symbol]
        while q:
            ep = open_ep.get(t.symbol)
            if ep is None:  # opening from flat
                ep = Episode(t.symbol, "L" if q > 0 else "S", t.date, t.seq)
                open_ep[t.symbol] = ep
            if not book or (book[0][0] > 0) == (q > 0):  # adding
                pos = sum(l[0] for l in book)
                if book:
                    avg = sum(l[0] * l[1] for l in book) / pos
                    worse = t.price < avg * 0.97 if q > 0 else t.price > avg * 1.03
                    ep.adds_below_avg += worse
                else:
                    worse = False
                    ep.first_qty = abs(q)
                book.append([q, t.price, ep.entries])
                ep.entry_qty.append(abs(q))
                ep.entry_pnl.append(0.0)
                ep.entry_worse.append(bool(book[:-1]) and worse)
                ep.entries += 1
                ep.max_qty = max(ep.max_qty, abs(pos + q))
                ep.orders.append(t)
                q = 0
                break
            # reducing
            if not ep.orders or ep.orders[-1] is not t:
                ep.orders.append(t)
            while q and book and (book[0][0] > 0) != (q > 0):
                lot = book[0]
                m = min(abs(q), abs(lot[0]))
                sign = 1 if lot[0] > 0 else -1
                ep.pnl += (t.price - lot[1]) * m * sign
                ep.entry_pnl[lot[2]] += (t.price - lot[1]) * m * sign
                lot[0] -= m * sign
                q += m * sign
                if lot[0] == 0:
                    book.pop(0)
            if not book:
                ep.end_date, ep.end_seq = t.date, t.seq
                done.append(open_ep.pop(t.symbol))
    done.extend(open_ep.values())  # still-open positions (unrealised, pnl so far)
    done.sort(key=lambda e: (e.start_date, e.start_seq))
    return done


def estimate_charges(trades: list[Trade]) -> float:
    """Indian retail discount-broker charges (approximate, FY26 rates).

    Options: ₹20/order, STT 0.1% on sell premium, exchange 0.0353%,
    SEBI ₹10/cr, stamp 0.003% on buy, GST 18% on (brokerage+exchange+SEBI).
    Equity intraday: min(₹20, 0.03%), STT 0.025% sell, exchange 0.00297%.
    """
    total = 0.0
    for t in trades:
        v = t.value
        if is_option(t.symbol):
            brk, exch = 20.0, v * 0.000353
            stt = v * 0.001 if t.side == "Sell" else 0.0
        else:
            brk, exch = min(20.0, v * 0.0003), v * 0.0000297
            stt = v * 0.00025 if t.side == "Sell" else 0.0
        sebi = v * 1e-6
        stamp = v * 0.00003 if t.side == "Buy" else 0.0
        total += brk + exch + stt + sebi + stamp + 0.18 * (brk + exch + sebi)
    return total


def daily_pnl(episodes: list[Episode]) -> dict:
    out: dict = defaultdict(float)
    for e in episodes:
        if not e.is_open:
            out[e.end_date] += e.pnl
    return dict(sorted(out.items()))
