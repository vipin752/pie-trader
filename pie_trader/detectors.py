"""Behavioural mistake detectors.

Each detector looks at episodes (flat→flat positions) and returns a Finding
with the money attributable to the behaviour, evidence, and the rule that
prevents it. Impacts overlap (one bad trade can be both revenge and
oversized), so findings are ranked, never summed.
"""
from __future__ import annotations

import statistics
from collections import Counter, defaultdict
from dataclasses import dataclass, field

from .fmt import inr
from .models import Episode, Trade, is_option


@dataclass
class Finding:
    code: str
    title: str
    severity: str  # critical | high | medium | low
    impact: float  # ₹ P&L of the affected episodes (negative = cost)
    count: int
    summary: str
    fix: str
    evidence: list = field(default_factory=list)


def _pnl(eps):
    return sum(e.pnl for e in eps)


def _sev(impact, gross_loss):
    share = -impact / gross_loss if gross_loss else 0
    return "critical" if share > 0.75 else "high" if share > 0.4 else "medium" if share > 0.15 else "low"


def _ev(e: Episode):
    return {
        "date": str(e.start_date),
        "symbol": e.symbol,
        "max_qty": e.max_qty,
        "entries": e.entries,
        "pnl": round(e.pnl),
    }


def detect(trades: list[Trade], episodes: list[Episode], rules) -> list[Finding]:
    closed = [e for e in episodes if not e.is_open]
    # severity is judged against the overall loss (or gross losses if net positive)
    total = sum(e.pnl for e in closed)
    gross_loss = -total if total < 0 else (-sum(e.pnl for e in closed if e.pnl < 0) or 1.0)
    out: list[Finding] = []

    # 1. Expiry-day option buying (theta/gamma lottery)
    ex = [e for e in closed if e.expiry_day and is_option(e.symbol)]
    if ex:
        rest = [e for e in closed if not e.expiry_day and is_option(e.symbol)]
        out.append(Finding(
            "EXPIRY_DAY", "Trading contracts on their expiry day", _sev(_pnl(ex), gross_loss),
            _pnl(ex), len(ex),
            f"{len(ex)} positions in contracts expiring that same day lost {inr(-_pnl(ex), sign=False)}. "
            f"All other option trades together made {inr(_pnl(rest))}.",
            "No new positions in a contract on its expiry day. Trade the next expiry, or skip the day.",
            [_ev(e) for e in sorted(ex, key=lambda e: e.pnl)[:8]],
        ))

    # 2. Size escalation vs the trader's own normal size (in lots)
    def lots(e):
        return e.max_qty / rules.lot_size(e.underlying)

    sized = [e for e in closed if is_option(e.symbol) and e.underlying in rules.lot_sizes]
    if sized:
        base = statistics.median(lots(e) for e in sized)
        big = [e for e in sized if lots(e) > max(rules.max_lots, 2 * base)]
        if big:
            out.append(Finding(
                "OVERSIZE", "Position size escalation", _sev(_pnl(big), gross_loss), _pnl(big), len(big),
                f"Your usual size is {base:g} lot(s). {len(big)} positions went above "
                f"{max(rules.max_lots, 2 * base):g} lots (up to {max(lots(e) for e in big):g}) and netted {inr(_pnl(big))}.",
                f"Hard cap: {rules.max_lots} lot(s) per position, enforced before the order goes out.",
                [_ev(e) for e in sorted(big, key=lambda e: e.pnl)[:8]],
            ))

    # 3. Revenge re-entry: a new episode in the same underlying soon after a loss, same day
    by_day = defaultdict(list)
    for e in closed:
        by_day[e.start_date].append(e)
    revenge = []
    for day, eps in by_day.items():
        eps.sort(key=lambda e: e.start_seq)
        last_loss = {}
        for e in eps:
            if e.underlying in last_loss and e.start_seq >= last_loss[e.underlying]:
                revenge.append(e)
            if e.pnl < 0 and e.end_date == day:
                last_loss[e.underlying] = e.end_seq
    if revenge:
        out.append(Finding(
            "REVENGE", "Re-entering straight after a loss", _sev(_pnl(revenge), gross_loss),
            _pnl(revenge), len(revenge),
            f"{len(revenge)} positions were opened in the same index right after a losing one on the same day. "
            f"Win rate on those: {sum(e.win for e in revenge) / len(revenge):.0%}, net {inr(_pnl(revenge))}.",
            f"Wait {rules.cooldown_minutes} min after any loss. Stop for the day after "
            f"{rules.max_consecutive_losses} losses in a row.",
            [_ev(e) for e in sorted(revenge, key=lambda e: e.pnl)[:8]],
        ))

    # 4. Averaging down a losing long option
    avg = [e for e in closed if e.adds_below_avg > 0]
    if avg:
        out.append(Finding(
            "AVERAGING", "Averaging down losing positions", _sev(_pnl(avg), gross_loss), _pnl(avg), len(avg),
            f"{len(avg)} positions were added to at worse prices ({sum(e.adds_below_avg for e in avg)} adds). "
            f"Net {inr(_pnl(avg))}, win rate {sum(e.win for e in avg) / len(avg):.0%}.",
            "Never add to a loser. Add only to a winner, and only after the stop has moved to breakeven.",
            [_ev(e) for e in sorted(avg, key=lambda e: e.pnl)[:8]],
        ))

    # 5. Kept trading after the daily loss limit was hit
    after = []
    for day, eps in by_day.items():
        run = 0.0
        for e in sorted(eps, key=lambda e: e.start_seq):
            if run <= -rules.max_daily_loss:
                after.append(e)
            run += e.pnl
    if after:
        out.append(Finding(
            "NO_DAILY_STOP", "Kept trading past a sane daily loss limit", _sev(_pnl(after), gross_loss),
            _pnl(after), len(after),
            f"After the day was already down {inr(rules.max_daily_loss, sign=False)}, {len(after)} more positions were taken: "
            f"net {inr(_pnl(after))}.",
            f"Hard daily stop at {inr(-rules.max_daily_loss)}. Close the platform once it is hit.",
            [_ev(e) for e in sorted(after, key=lambda e: e.pnl)[:8]],
        ))

    # 6. Overtrading
    orders = Counter(t.date for t in trades)
    dpnl = defaultdict(float)
    for e in closed:
        dpnl[e.end_date] += e.pnl
    busy = [d for d, n in orders.items() if n > rules.max_orders_per_day]
    if busy:
        bp = sum(dpnl[d] for d in busy)
        calm = sum(v for d, v in dpnl.items() if d not in busy)
        out.append(Finding(
            "OVERTRADING", "Overtrading", _sev(min(bp, 0), gross_loss), bp, len(busy),
            f"{len(busy)} of {len(orders)} days had more than {rules.max_orders_per_day} orders "
            f"(peak {max(orders.values())}). Those days: {inr(bp)}. Other days: {inr(calm)}.",
            f"No more than {rules.max_trades_per_day} positions or {rules.max_orders_per_day} orders a day.",
            [{"date": str(d), "orders": orders[d], "pnl": round(dpnl[d])} for d in sorted(busy, key=lambda d: dpnl[d])[:8]],
        ))

    # 7. Carrying short-dated options overnight
    on = [e for e in closed if e.overnight and is_option(e.symbol) and (e.dte_at_entry or 99) <= 3]
    if on:
        out.append(Finding(
            "OVERNIGHT", "Holding weekly options overnight", _sev(_pnl(on), gross_loss), _pnl(on), len(on),
            f"{len(on)} options with 3 or fewer days to expiry were held overnight and netted {inr(_pnl(on))}. "
            "Gaps and overnight time decay work against the buyer.",
            "Close any option with 3 or fewer days to expiry before 15:15. No exceptions on the day before expiry.",
            [_ev(e) for e in sorted(on, key=lambda e: e.pnl)[:8]],
        ))

    # 8. Payoff asymmetry: losers bigger than winners
    w = [e.pnl for e in closed if e.pnl > 0]
    l = [e.pnl for e in closed if e.pnl < 0]
    if w and l:
        aw, al = statistics.mean(w), statistics.mean(l)
        if aw < abs(al) * 1.5:
            need = abs(al) / (aw + abs(al))
            out.append(Finding(
                "PAYOFF", "Small winners, big losers", "high" if aw < abs(al) else "medium",
                sum(e.pnl for e in closed), len(closed),
                f"Average win {inr(aw, sign=False)} vs average loss {inr(al, sign=False)} (payoff {aw / abs(al):.2f}). "
                f"At this payoff you would need a {need:.0%} win rate to break even. Your win rate is {len(w) / len(closed):.0%}.",
                f"Exit every position at a stop of {rules.stop_loss_pct:.0%} of premium. Take profit at 2× the risk or more, "
                "or trail the stop. Aim for a payoff of at least 1.5.",
                [{"avg_win": round(aw), "avg_loss": round(al), "win_rate": f"{len(w) / len(closed):.0%}"}],
            ))

    order = {"critical": 0, "high": 1, "medium": 2, "low": 3}
    out.sort(key=lambda f: (order[f.severity], f.impact))
    return out
