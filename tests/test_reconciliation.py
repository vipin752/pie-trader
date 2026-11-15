"""Reconciliation against the owner's real broker statements (QA master
prompt Suite J / LED-020). The statement files are the owner's personal
financial data — never committed (see .gitignore's *.xlsx rule) — so these
tests skip cleanly when the fixtures aren't present, e.g. in CI.

Two checks, each honest about what it can and can't prove:

1. Parser fidelity: does our own row-by-row parse of the P&L statement sum
   to the SAME totals the broker printed on the summary page? This is a
   real reconciliation — it would catch a column-misalignment or
   double-counting bug in pnl_statement.py — and does not depend on the
   two files sharing an account.

2. Cross-file contract matching, which turned out NOT to be reconcilable:
   the two files carry different client ids (MA2785481 vs V50426406).
   The result is recorded as a finding, not silently discarded or forced
   into agreement (master prompt §14: "do not silently adjust internal
   values to force a match").
"""
import re
from pathlib import Path

import pytest

from pie_trader.models import expiry, option_type, underlying
from pie_trader.parsers import load_trades
from pie_trader.parsers.pnl_statement import load_pnl_statement
from pie_trader.pnl import build_episodes

STATEMENTS = Path(__file__).resolve().parent.parent / "test-data" / "statements"
PL_FILE = STATEMENTS / "profit_loss_statement.xlsx"
TH_FILE = STATEMENTS / "trade_history.xlsx"

pytestmark = pytest.mark.skipif(
    not (PL_FILE.exists() and TH_FILE.exists()),
    reason="real broker statement fixtures not present (private data, not committed)",
)


def test_parsed_fo_rows_sum_to_the_statements_own_printed_total():
    rows = load_pnl_statement(PL_FILE)
    fo = [r for r in rows if r.segment == "FO"]
    assert len(fo) > 0
    # Broker's own summary page: Total Gross PnL -340756.65, Net PnL -433494.03
    # (F&O sheet, "Derivative P&L Summary"). Reconciliation tolerance is a
    # few rupees for float/rounding noise across hundreds of rows, not a
    # license to paper over a real mismatch.
    assert sum(r.gross for r in fo) == pytest.approx(-340756.65, abs=5)
    assert sum(r.net for r in fo) == pytest.approx(-433494.03, abs=5)


def test_parsed_equity_rows_sum_to_the_statements_own_printed_total():
    rows = load_pnl_statement(PL_FILE)
    delivery = [r for r in rows if r.segment == "EQ_DELIVERY"]
    intraday = [r for r in rows if r.segment == "EQ_INTRADAY"]
    assert delivery and intraday
    # Broker's summary: Net PnL (delivery+intraday combined) -37514.29;
    # Intraday Net PnL alone -13761.85.
    assert sum(r.net for r in intraday) == pytest.approx(-13761.85, abs=5)
    assert sum(r.net for r in delivery) + sum(r.net for r in intraday) == pytest.approx(-37514.29, abs=5)


def test_trade_history_and_pl_statement_are_different_accounts_not_reconcilable():
    """Documents a real finding rather than hiding it: the two uploaded
    files carry different client ids, so contract-level reconciliation
    between them is not meaningful. Where a (underlying, expiry, strike,
    option) key happens to collide, the net P&L figures disagree by
    thousands of rupees — exactly what you'd expect from two unrelated
    accounts coincidentally trading the same strike, not a reconciliation
    break in either file."""
    th_trades = load_trades(TH_FILE)
    pl_rows = load_pnl_statement(PL_FILE)

    def key_from_trade_symbol(sym):
        m = re.search(r"(\d+(?:\.\d+)?) (CE|PE)$", sym)
        strike = float(m.group(1)) if m else None
        return (underlying(sym), expiry(sym), strike, option_type(sym))

    th_net = {}
    for ep in build_episodes(th_trades):
        if ep.is_open:
            continue
        k = key_from_trade_symbol(ep.symbol)
        th_net[k] = th_net.get(k, 0.0) + ep.pnl

    pl_net = {}
    for r in pl_rows:
        if r.segment != "FO" or r.expiry is None:
            continue
        k = (r.underlying, r.expiry, r.strike, r.opt)
        pl_net[k] = pl_net.get(k, 0.0) + r.net

    overlap = set(th_net) & set(pl_net)
    # Both files are the same person's trading, so SOME strike collisions
    # across ~90 vs ~660 distinct contracts are expected by chance alone.
    assert 0 < len(overlap) < 10, "overlap size outside the 'coincidence, not a shared account' range"
    for k in overlap:
        # If this were the SAME trade recorded twice, these would be close.
        # They are not — confirming the two files are unrelated accounts.
        assert abs(th_net[k] - pl_net[k]) > 1000
