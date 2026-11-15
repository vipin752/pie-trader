from collections import defaultdict


def assign_seq_from_trade_id(trades):
    """Approximate intraday order when the statement has no timestamps.

    Exchange trade numbers increase through the session but NSE and BSE use
    different number ranges, so each (date, exchange) group is ranked
    separately and mapped to 0..1; groups are then merged on that scale.
    """
    groups = defaultdict(list)
    for t in trades:
        groups[(t.date, t.exchange)].append(t)
    for g in groups.values():
        g.sort(key=lambda t: int(t.trade_id) if str(t.trade_id).isdigit() else 0)
        n = len(g)
        for i, t in enumerate(g):
            t.seq = (i + 0.5) / n
    trades.sort(key=lambda t: (t.date, t.seq))
    return trades
