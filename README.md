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

The Guard blocks or warns on: the daily loss limit, the post-target giveback floor (the daily target is a minimum — after banking it, risk halves and the day stops only if half the peak is given back), the first-loss stop (one booked loss → no more trades that day), losses in a row, the trade and order count, the cooldown, expiry-day entries, the lot cap, averaging down, per-trade risk at the stop, and new short-dated option positions after the `flat_by` time.

## Automated session engine

`pie_trader/auto/` turns the rules into an automated option strategy on market structure:

- **Structure (SMC/ICT first):** swing highs/lows on closed candles, **BOS**, **CHoCH**, **liquidity** (untaken swings, PDH/PDL) and sweeps. On top of that, `auto/smc.py` tracks **order blocks** (last opposite-close candle before a break, until mitigated or invalidated), **fair value gaps**, the **dealing range with premium/discount** (retracement longs only in discount, shorts only in premium) and the **OTE 62–79% pocket**.
- **Setups:** `sweep_bos` — liquidity is swept, then price breaks structure the other way; `trend_bos` — a BOS in the direction of the 15-minute trend; `pdhl_break` — a >80%-body close beyond the previous day's high/low (first candle is observation only); `ob_mitigation` — price returns to a fresh order block/FVG with the higher-timeframe trend and a confirming close; `or_fib` — a dominant-body close beyond the 138.2% extension of the opening 15-minute body (minimum 20-point body). Stop-and-reverse is never automatic: a failed PDH/PDL breakout only arms the reverse, which still needs its own BOS/CHoCH, sizing and Guard approval. Stops must clear an ATR noise floor. Every closed trade is journalled with the market regime and volatility state at entry (`--journal journal.json`).
- **Session filter:** three entry windows — 09:20, 11:30 and 13:45 (one trade per window by default); everything managed by 15:15.
- **Options:** ATM CE/PE on the nearest expiry that outlives the ride, so a position is **never held on its own expiry day**.
- **Sizing:** lots from ₹ risk at the stop (`risk_per_trade`), capped by `max_lots` and capital. Every order still passes the **Guard**.
- **Riding the trade:** two trail modes. `structure` — the stop follows each new protected swing after every BOS in our favour. `r_ratchet` (default preset) — the trade is only taken with ≥1:2 room; at +1R the stop moves to breakeven, at +2R it locks +1R, at +3R it locks +2R, and so on. Half is booked at +1R either way. Exits on CHoCH against, on opposing liquidity being swept, or on the **max 3-session time stop**. Overnight only with ≥1R of cushion and the stop at breakeven or better.

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

See `docs/ARCHITECTURE.md` (the owner's master spec with an implementation-status map), `docs/PLAYBOOK.md` (the distilled rulebook: what is enforced in code vs manual discipline vs waiting on data feeds), `pie_trader/auto/volatility.py` (per-timeframe ATR with percentile states) and `pie_trader/auto/regime.py` (price-based regime classification; VIX/PCR/OI/breadth are marked unavailable until a data feed exists — never invented) and `pie_trader/auto/direction.py` (Market Direction Engine: an independent read on 15m, 30m, 60m, Daily, Weekly and Monthly structure, combined into one overall direction with evidence per timeframe; optional `require_mtf_alignment` entry gate).

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
