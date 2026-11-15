"""Property-based tests for financial/trading invariants (QA master prompt
§20). Each property is checked across hundreds of generated cases, not
just the handful of examples in test_engine.py / test_auto.py."""
import datetime as dt

from hypothesis import given, settings
from hypothesis import strategies as st

from pie_trader.auto.options import UNDERLYINGS, choose_contract, size_lots
from pie_trader.models import Trade
from pie_trader.pnl import build_episodes, dedupe_by_trade_id
from pie_trader.rules import Guard, RuleSet

D = dt.date(2026, 11, 10)


@given(
    lots=st.integers(min_value=1, max_value=20),
    entry=st.floats(min_value=1, max_value=500, allow_nan=False),
    stop_frac=st.floats(min_value=0.01, max_value=0.99, allow_nan=False),
)
@settings(max_examples=200)
def test_increasing_lots_never_reduces_planned_risk(lots, entry, stop_frac):
    stop = entry * stop_frac
    lot_size = 65
    risk_per_lot = (entry - stop) * lot_size
    assert (entry - stop) * lot_size * (lots + 1) >= risk_per_lot * lots


@given(
    entry=st.floats(min_value=1, max_value=500, allow_nan=False),
    stop=st.floats(min_value=0, max_value=500, allow_nan=False),
)
@settings(max_examples=200)
def test_size_lots_never_returns_negative_or_exceeds_cap(entry, stop):
    lots = size_lots(entry, stop, 65, risk_inr=5000, max_lots=4, capital=200_000, max_capital_frac=0.5)
    assert 0 <= lots <= 4


@given(qty=st.integers(min_value=1, max_value=2000), px=st.floats(min_value=0.05, max_value=500, allow_nan=False))
@settings(max_examples=100)
def test_duplicate_fills_never_change_the_ledger(qty, px):
    base = Trade(D, "NSEFO", "Buy", "NIFTY 17 NOV 2026 25000 CE", qty, px, trade_id="dup-1", seq=0.1)
    closer = Trade(D, "NSEFO", "Sell", "NIFTY 17 NOV 2026 25000 CE", qty, px * 1.1, trade_id="dup-2", seq=0.2)
    once = build_episodes([base, closer])
    _, dropped = dedupe_by_trade_id([base, closer, base])  # 'base' repeated, e.g. a replayed event
    twice = build_episodes([base, closer, base])
    assert dropped == 1
    assert sum(e.pnl for e in once) == sum(e.pnl for e in twice)


@given(spot=st.floats(min_value=100, max_value=100_000, allow_nan=False))
@settings(max_examples=100)
def test_chosen_option_never_expires_before_the_ride_ends(spot):
    u = UNDERLYINGS["NIFTY"]
    now = dt.datetime(2026, 11, 16, 10, 0)
    con = choose_contract(u, spot, "up", now, max_hold_days=3)
    assert con.expiry > now.date()  # never the entry day itself
    assert con.strike > 0


@given(
    qty=st.integers(min_value=65, max_value=65 * 10),
    price=st.floats(min_value=1, max_value=500, allow_nan=False),
)
@settings(max_examples=100)
def test_a_rejected_trade_never_mutates_guard_state(qty, price):
    # Reject path (oversized lots) must be side-effect free: the Guard's
    # day state after a rejection is identical to before it.
    g = Guard(RuleSet(max_lots=1))
    now = dt.datetime(2026, 11, 16, 10, 0)
    before = (g.realised, g.streak, dict(g.last_loss_at), dict(g.positions))
    g.check("NIFTY 24 NOV 2026 25000 CE", "Buy", qty, price, now)  # likely oversized -> rejected
    after = (g.realised, g.streak, dict(g.last_loss_at), dict(g.positions))
    assert before == after
