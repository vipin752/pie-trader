# Reconciliation Report

Against the owner's two real uploaded broker files:
`test-data/statements/trade_history.xlsx` (Mirae trade-by-trade export) and
`test-data/statements/profit_loss_statement.xlsx` (a P&L summary statement).
Neither is committed to git (personal financial data — `.gitignore` excludes
all `*.xlsx`); both are read directly by `tests/test_reconciliation.py`,
which skips cleanly if they're absent (e.g. in CI).

## 1. Parser fidelity — PASSED

Our own row-by-row parse of `profit_loss_statement.xlsx` (933 F&O rows, 94
equity-delivery rows, 50 equity-intraday rows) was summed and compared
against the numbers the broker itself printed on the statement's own
summary page:

| Segment | Metric | Broker's printed total | Our parsed sum | Match |
|---|---|---|---|---|
| F&O | Gross P&L | ₹−340,756.65 | same, within ₹5 | ✅ |
| F&O | Net P&L | ₹−433,494.03 | same, within ₹5 | ✅ |
| Equity (intraday) | Net P&L | ₹−13,761.85 | same, within ₹5 | ✅ |
| Equity (delivery + intraday) | Net P&L | ₹−37,514.29 | same, within ₹5 | ✅ |

This is a real correctness check on `pie_trader/parsers/pnl_statement.py`:
a column-misalignment, a double-counted charge column, or a dropped row
would have broken one of these sums. It did not.

## 2. Cross-file reconciliation — NOT APPLICABLE (finding, not a defect)

`trade_history.xlsx` header reads **Client ID: MA2785481**.
`profit_loss_statement.xlsx` header reads **Client Id: V50426406**.

Same person (`VIPIN KUMAR DUBE` on both), **different account/client
identifiers**. Joining the two files on `(underlying, expiry, strike,
option type)` found only **2 coincidental key collisions** out of ~90
distinct contracts in the trade history and ~663 in the P&L statement —
and on both, the net P&L figures disagree by more than ₹1,000:

| Contract | trade_history net | P&L statement net | Difference |
|---|---|---|---|
| NIFTY 13-Oct-2026 22650 CE | +₹7,833 | −₹5,380 | ₹13,213 |
| NIFTY 13-Oct-2026 22700 CE | +₹114 | −₹1,751 | ₹1,865 |

This is exactly what you'd expect from **two unrelated accounts** that
happened to both trade the same strike on the same day by chance — not the
same trade recorded twice, and not a reconciliation break in either file.
Per the master prompt's own instruction ("do not silently adjust internal
values to force a match"), this is recorded as **FINDING-001** in the
defect register, not glossed over or forced into agreement.

**If these are in fact meant to be the same trading relationship** (e.g.
one client id is an older UCC code and the other a newer unified login),
the owner should confirm that with the broker — at that point a real
transaction-level reconciliation becomes possible and this report should
be re-run.
