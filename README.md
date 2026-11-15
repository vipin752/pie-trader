# pie-trader

PIE Trader helps traders build discipline. **P**lan the risk, keep your **I**ntegrity to the rules, and **E**xecute only what the plan allows.

It does three things:

1. **Shows what really happened.** It reads your broker trade history, matches positions first-in first-out, estimates charges, and gives the net P&L.
2. **Names the mistakes, in rupees.** Expiry-day gambling, oversizing, revenge re-entry, averaging down, no daily stop, overtrading, holding weekly options overnight, and losers bigger than winners. Each one is measured from your own trades.
3. **Enforces the fix.** A rule set (`rules.toml`) is replayed against the same history to show what it would have saved. The same rules run live through `Guard`, a pre-trade check you put in front of any broker order API.

## Quick start

```bash
pip install -e .
```

```bash
pie analyse path/to/TradeHistory.xlsx --rules rules.toml --out report.html
```

Supported inputs:
- Mirae Asset / m.Stock "Trade History" `.xlsx`
- A generic CSV with columns `date,time,exchange,side,symbol,qty,price,trade_id`. The `time` column is optional but improves accuracy.

Broker statements are personal data. `.gitignore` keeps `*.xlsx` and generated reports out of the repo.

## Live guard

```python
from pie_trader.rules import Guard, RuleSet
g = Guard(RuleSet.load("rules.toml"))

d = g.check("NIFTY 17 NOV 2026 25350 CE", "Buy", 130, 63.5, now)
if not d:
    print(d.reasons)        # e.g. "Cooldown after a loss on NIFTY: 9 min left."
else:
    place_order(...); g.on_fill(...)
# when a position closes:
g.on_close(symbol, realised_pnl, now)
```

The Guard blocks or warns on: the daily loss limit, losses in a row, the trade and order count, the cooldown, expiry-day entries, the lot cap, averaging down, per-trade risk at the stop, and new short-dated option positions after the `flat_by` time.

## Automated session engine

`pie_trader/auto/` turns the rules into an automated option strategy on market structure:

- **Structure:** swing highs/lows on closed candles, **BOS** (break of structure), **CHoCH** (change of character) and **liquidity** (untaken swings, equal highs/lows, previous-day high/low). Sweeps are wicks through a pool that close back inside.
- **Setups:** `sweep_bos` — liquidity is swept, then price breaks structure the other way; `trend_bos` — a BOS in the direction of the 15-minute trend.
- **Session filter:** entries only inside configured windows (default 09:30–11:30 and 13:15–14:45); everything managed by 15:15.
- **Options:** ATM CE/PE on the nearest expiry that outlives the ride, so a position is **never held on its own expiry day**.
- **Sizing:** lots from ₹ risk at the stop (`risk_per_trade`), capped by `max_lots` and capital. Every order still passes the **Guard**.
- **Riding the trade:** half booked at +1R and the stop moves to breakeven; then the stop trails each new protected swing after every BOS in our favour. Exits on CHoCH against, on opposing liquidity being swept, or on the **max 3-session time stop**. Overnight only with ≥1R of cushion and the stop at breakeven or better.

```bash
pie backtest candles.csv --config rules.toml --report bot-report.html   # your own 5m underlying data
pie backtest                                                            # synthetic demo, results meaningless
pie auto webhook …  # see below
```

`candles.csv` columns: `datetime,open,high,low,close[,volume]` (5-minute bars of the underlying, IST).
The backtest prices options with Black–Scholes as a stand-in — good for logic testing, not for live P&L claims. The bot's own fills can be fed back through the discipline report (`--report`).

### TradingView / broker connection

```bash
pie webhook --secret <random> --port 8787
```

Point a TradingView alert at `http://<your-host>:8787/tv` with message
`{"secret":"<random>","signal":"bos_up","price":{{close}},"time":"{{timenow}}"}`.
TradingView only reports what happened on the chart; PIE still applies the session window, option selection, sizing and the Guard. Live broker adapters plug into `auto/broker.py::make_broker` and require `live=True` plus your API keys in environment variables — **paper mode is the default everywhere**.

## Layout

```
pie_trader/
  parsers/     broker file → Trade list (ordering from trade id when there are no timestamps)
  pnl.py       FIFO episodes (flat→flat positions), charges, daily P&L
  detectors.py behavioural findings with ₹ impact and the rule that fixes each
  rules.py     RuleSet (TOML), historical replay, per-rule impact, live Guard
  report.py    self-contained HTML report
  cli.py       `pie analyse` / `pie backtest` / `pie webhook`
  auto/        automated engine: candles, structure (BOS/CHoCH/liquidity),
               option selection, sizing, paper broker, backtest, TV webhook
design/        PIE design system: tokens.json, pie.css, README
tests/
```

## Limits of the replay

Statements have no tick data. The replay can **skip** trades the rules forbid, **cap** size and **remove** averaging-down adds. It cannot simulate an earlier exit at a stop. Stops only shrink losses, so the "with rules" figure is a conservative floor.

## Roadmap

- [ ] More broker parsers: Zerodha tradebook, Upstox, Angel, Dhan
- [ ] Contract-note parser for exact charges
- [ ] Minute-candle replay so stop-loss and take-profit exits can be simulated
- [ ] Live broker adapter (m.Stock / Angel One / Dhan / Zerodha / Upstox) with Guard in front of `place_order`, plus a kill switch
- [ ] Pre-market plan: bias, levels and max risk written down before 09:15. The Guard rejects trades that aren't in the plan
- [ ] Journal: each trade tagged with setup and emotion, then reviewed weekly
