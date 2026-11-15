# PIE Trader — Master Architecture Specification
## Independent Multi-Timeframe Intelligence & Adaptive Trading Engine

This is the owner's target specification. The table at the end maps each part
to its current implementation status in this repository.

## 1. Core architectural decision
Independent Dynamic Multi-Timeframe Architecture. No permanently fixed
timeframe. Each analytical function independently selects its timeframe from
market regime, volatility and India VIX, price/candle structure, buyer–seller
pressure, option-chain and futures activity, liquidity, historical setup
performance, time of day and expiry conditions. Different timeframes can be
used simultaneously for one trade (e.g. direction 15m, confirmation 5m, setup
3m, entry 1m, trailing on ticks, context 60m/Daily) — illustrative, not fixed.

## 2. Independent engines
- **A. Market Direction Engine** — 15m/30m/60m/Daily structure, trend strength, key levels, futures and breadth confirmation.
- **B. Trend Confirmation Engine** — 3m/5m/15m; continuation vs weakening vs reversal; detects HTF↔LTF conflicts; does not treat every countertrend move as a reversal.
- **C. Setup Detection Engine** — breakout, breakout-retest, pullback, range rejection, liquidity sweep, reversal; objective conditions only; records timeframe + evidence per setup.
- **D. Entry Optimisation Engine** — 1m/3m triggers, candle confirmation, no chasing extended candles, spread/liquidity/slippage checks; a valid setup ≠ a valid entry price.
- **E. ATR & Volatility Engine** — ATR per timeframe, configurable periods, candle-range and realised-vol percentiles, expansion/contraction; never assumes one ATR fits all.
- **F. Tick-Based Trade Management Engine** — live premium monitoring; trailing ladder +1R→BE, +2R→+1R, +3R→+2R, …; never moves a stop backwards; records broker acknowledgement for every modification.

## 3. Market regime classification
Regimes: strong/weak bull, strong/weak bear, sideways, low-vol compression,
high-vol expansion, volatility reversal, false breakout/liquidity sweep,
event-driven, conflicting signals, insufficient data.
India VIX evaluated by value, percentile, rate of change, intraday movement,
relation to realised vol and IV across strikes. VIX is a volatility input,
never a directional prediction.

## 4. Buyer–seller intelligence
Option chain (OI, ΔOI, volume, strike-wise and ATM behaviour, IV, Greeks,
bid/ask), futures (price, volume, OI, basis), order flow (imbalance, spread,
aggression, depth, liquidity deterioration), underlying (price, volume,
breadth, A/D, sectors, heavyweights). Never infer direction from CE/PE OI
alone. Where reliable classification is unavailable, mark the input
unavailable instead of inventing it.

## 5. Dynamic timeframe selection
Per task: read available data and quality → regime/vol → historical
performance of candidate combinations → select → check HTF/LTF confirmation →
record the reason → re-evaluate on material change. Hysteresis and minimum
persistence to prevent flip-flopping. Every selection stores timeframe,
alternatives, regime, vol, confirmations, reason, timestamp, version.

## 6. Adaptive strategy selection (after regime classification)
| Regime | Candidate setup |
|---|---|
| Strong trend | Trend continuation |
| Pullback in trend | Pullback entry |
| Volatility expansion | Confirmed breakout |
| Compression | Compression breakout |
| Range-bound | S/R rejection |
| Liquidity sweep | Sweep and reclaim |
| Reversal | Confirmed structure reversal |
| Conflicting | Wait / NO_TRADE |

Candidates, not guaranteed signals; each objectively defined and independently backtested.

## 7. Adaptive stop and target
No universal fixed premium stop or ATR multiplier. Stops must represent
actual setup invalidation above normal noise: structure, swings, range
distribution, multi-TF ATR, premium behaviour, spread, IV, theta, costs.
Smallest stop ≠ best stop: optimise net expectancy. Record underlying
invalidation, premium exit level, planned risk, executed risk, realised P&L
separately. Minimum net R:R 1:2; prefer 1:3/1:5 only with evidence.

## 8. Decision outputs
BULLISH / BEARISH / SIDEWAYS / VOLATILITY_EXPANSION / VOLATILITY_CONTRACTION /
REVERSAL_POSSIBLE / CONFLICTING_SIGNALS / NO_TRADE — with separate outputs for
direction, setup, entry confirmation, option selection, risk approval,
execution. A directional signal never auto-authorises a trade.

## 9–10. Services, topics and APIs (target production shape)
Ingestion, candle aggregation, ATR/vol analytics, option-chain and futures
intelligence, buyer–seller analytics, regime detection, timeframe selection,
setup detection, entry optimisation, stop/target optimisation, capital,
risk, execution, tick position management. Kafka topics `pie.market.*`,
`pie.analytics.*`, `pie.trade.*`, `pie.risk.*`, `pie.broker.*`,
`pie.alerts`, `pie.squareoff.signals`. REST APIs under `/api/market`,
`/api/options`, `/api/analytics`, `/api/trade-card`, `/api/risk` — every
response with timestamp, freshness, selected timeframe, confidence/evidence
and reason codes.

## 11. Research and validation
Chronological train/validation/out-of-sample, walk-forward, no look-ahead or
survivorship bias. Evaluate expectancy, win rate, PF, max DD, avg R:R,
MAE/MFE, stop-out frequency, slippage and charges, by regime and timeframe
combination. No promotion to paper on training performance alone.

## 12. Standing configuration
Capital ₹2,00,000 · risk budget 5% of applicable basis (final basis TBC) ·
minimum position 6 lots · max 3 trades/day · stop after 2 losing trades ·
min net R:R 1:2 · tick-based trailing · no averaging losers · re-entry only
with higher later-setup confidence and all risk checks · live contract
specs/lot sizes · reject the trade if the minimum position cannot fit inside
risk and margin · BTST separate from intraday.

## 13. Technology

**Updated 2026-11-16: anchored on Java 25 LTS** (owner's decision), superseding
the Java 21 line below. Every version here was checked live against Maven
Central / npm on that date — re-check before trusting this file months later.

| Layer | Choice | Why, grounded |
|---|---|---|
| **Core backend** (deterministic execution, risk, APIs, security) | **Java 25 (LTS)**, Spring Boot **4.1.2** | 25 is the first LTS after 21 (Sept 2025). Confirmed installed and building clean in this environment (`openjdk 25.0.5.0.3`); `java/pie-risk-engine` is a working proof — `mvn test`/`package` both pass on it. 4.1.2 is Maven Central's latest *stable* Spring Boot (4.2.0 is still RC) and runs on Spring Framework 7 / Jakarta EE, fully supporting 25. Build tool: **Maven**, not Gradle — Gradle 8.14 can target a Java 25 *toolchain* but running Gradle's own process on 25 adds friction not worth fighting pre-Phase-1; revisit once there are enough modules to want a Gradle version catalog. |
| **Messaging** | Kafka, `kafka-clients` **4.4.0**, `spring-kafka` **4.1.2** | Latest stable on Maven Central. Topics as listed in §9. |
| **Datastores** | PostgreSQL (driver **42.7.14**), Redis | Postgres for the audit trail / journal / configs (spec §9); Redis for live state and the shared "what's open right now" the Guard/Portfolio layer needs across instances. |
| **Quant/research/ML** | Python, FastAPI | Unchanged — this is still where `pie_trader/` lives today. Python analytics **never** bypasses the Java risk engine; it proposes, Java approves (spec §13's own rule, kept). |
| **Frontend** | Next.js **16.4.6**, React, TypeScript | Latest stable on npm as of this date. |
| **CI/CD** | Docker + whatever CI the team already runs | Not re-decided here; nothing version-specific to ground. |

### AI/ML — where it fits and where it is kept out

Two separate things both get called "AI" in this project; they must not be
confused with each other:

1. **Quantitative / statistical modelling** (the actual decision-support
   work in §2, §5, §7, §11): classical, auditable methods first —
   regression/classification for regime and setup-quality calibration
   (e.g. scikit-learn), walk-forward validated, every output traceable to
   its inputs (spec §14.10). A sequence model (e.g. PyTorch) is a later
   escalation, used only if a classical model demonstrably can't capture
   something and the escalation itself is backtested and out-of-sample
   validated like any other strategy change (spec §11, §14.9) — never
   adopted because deep learning sounds more impressive. **This layer never
   outputs a trade; it outputs a feature or a calibrated probability that
   feeds the deterministic Setup/Risk engines.**
2. **An LLM (e.g. Claude) for explanation, not decision**: narrating the
   *already-recorded* structured decision trail (regime, setup, evidence,
   R:R, stop/target reasoning — everything the journal in §9 captures) into
   a weekly/monthly review a human reads, or answering "why did it take
   this trade" from the journal's own fields. It must never be given the
   power to approve a trade, size a position, or move a stop — that would
   violate the same rule that keeps Python analytics out of the Java risk
   path (spec §13, §14.5/§14.6). If it ever drafts a parameter change (e.g.
   "this stop model underperforms in COMPRESSION regime, consider X"), that
   suggestion goes through the same backtest/walk-forward/out-of-sample
   gate as a human's idea would — spec §14.9 makes no exception for where
   an idea came from.

## 14. Non-negotiables
1. No permanently fixed analytical timeframe. 2. No universal ATR/stop.
3. No direction from a single indicator. 4. No option-chain reads from OI
alone. 5. No trade without validated setup + risk approval. 6. No execution
on stale data. 7. No look-ahead in backtests. 8. No uncalibrated confidence
numbers. 9. No promotion without out-of-sample evidence. 10. Every decision
explainable, reproducible, auditable.

## 15. Development sequence
Data collection → aggregation/quality → multi-TF ATR/vol → option-chain and
futures intelligence → buyer–seller analytics → regime detection → timeframe
selection → setup detection/entry optimisation → adaptive stop/target →
capital and risk integration → backtest + walk-forward → paper → fill
comparison → live only after explicit approval.

---

## Implementation status in this repository

| Spec item | Status |
|---|---|
| Trailing ladder (+1R→BE, +2R→+1R, …) | ✅ `auto/engine.py` (`trail="r_ratchet"`), candle-based now, tick-based needs a live feed |
| Liquidity sweeps, BOS/CHoCH, PDH/PDL | ✅ `auto/structure.py` |
| Multi-TF (exec + HTF bias + daily levels) | ✅ partial: 5m exec, 15m bias, daily PDH/PDL. Full independent selection engine: pending |
| Market Direction Engine (15m/30m/60m/Daily/**Weekly/Monthly**) | ✅ `auto/direction.py` — independent Structure per timeframe, combined into one overall read; D/W/M agreement decides STRONG vs WEAK. Optional entry gate (`require_mtf_alignment`, off by default). Weekly/Monthly read as INSUFFICIENT_DATA until enough calendar weeks/months of candles exist |
| ATR & volatility per timeframe, percentiles | ✅ `auto/volatility.py` |
| Market regime classification | ✅ first version: `auto/regime.py` (price-based; VIX/breadth inputs pending data feeds) |
| PDH/PDL breakout setup, 26-SMA filter, stop-and-reverse | ✅ `auto/engine.py` |
| Guard: daily loss, first-loss stop, target-minimum + giveback floor, cooldown, lot caps, expiry ban | ✅ `rules.py` |
| Option chain / futures / PCR / VIX / breadth intelligence | ⬜ needs market-data feeds (broker API or NSE data) — inputs are marked unavailable, never invented |
| SMC/ICT: order blocks, FVG, premium/discount, OTE | ✅ `auto/smc.py`, setups `ob_mitigation`/`or_fib` use it; `pd_filter`/`ote_filter` toggles |
| Dual-chart analysis (Chart A underlying, Chart B option premium, neither alone authorises) | ✅ Chart A = structure/SMC/regime on the underlying (as above); Chart B = `auto/premium.py` (`validate_premium`) checks the **selected contract's** premium floor and modelled spread before `_open` proceeds; both must pass. The spread model is a stand-in for a real bid/ask — flagged in its own docstring, not a quote |
| Multi-index scope (NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY, SENSEX) | ✅ `auto/instruments.py` (`SUPPORTED_INDICES`, `InstrumentMaster`, loadable from a refreshed scrip-master file, `instruments.sample.json`) — static fallback clearly labelled, never trusted as a live source |
| Exchange & Contract Intelligence (lot size, strike step, expiry weekday, holidays, staleness) | ✅ `auto/instruments.py` — `is_stale()`, `is_trading_day()`, `load_file()`; `refresh_from_broker()` raises until a validated adapter exists (same posture as the Angel One scaffold) |
| Portfolio-level exposure & correlated-risk control | ✅ `auto/portfolio.py` (`PortfolioGuard`): default **1 position across the whole portfolio**, blocks a second index opening while any correlated one (default: all five) is already open; `Candidate`/`rank()` for comparing setups across indices. `auto/backtest.py::run_portfolio_backtest` steps multiple indices in one shared chronological order so the guard's state is honoured in real time, not per-index in isolation — proven in `test_portfolio_backtest_blocks_simultaneous_correlated_positions` |
| Independently configurable strategy per index | ✅ mechanically — each index gets its own `AutoConfig`/`RuleSet`; ⬜ no per-index threshold research done yet (needs real data per index, spec §6) |
| Dynamic timeframe selection with hysteresis + audit log | ⬜ pending |
| Kafka/Java/Redis/Postgres production split | ⬜ pending — current Python package is the research/paper core |
| Walk-forward validation harness | ⬜ pending real historical data |
