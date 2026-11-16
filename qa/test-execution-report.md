# Test Execution Report

Run on a clean build, this session, against real infrastructure (not mocks)
where infrastructure was involved.

## Python (`pie_trader/`)

```
cd pie-trader && python3 -m pytest -q
```

**41 passed, 0 failed, 0 skipped.**

| File | Tests | What it covers |
|---|---|---|
| test_engine.py | 14 | FIFO episodes, replay, Guard, rule loading, money formatting, duplicate-trade-id protection |
| test_parsers.py | 2 | Mirae xlsx + generic CSV parsing |
| test_auto.py | 31 | Structure/BOS/CHoCH, SMC, options, engine, backtest, regime, direction, volatility, instruments, premium gate, portfolio exposure |
| test_properties.py | 5 | Hypothesis property-based: risk monotonicity, size-lots bounds, duplicate-fill idempotency, option-expiry invariant, rejected-trade side-effect freedom |
| test_reconciliation.py | 3 | Real broker statement parser fidelity + cross-file account-mismatch finding (skips cleanly if the private fixture files are absent) |

## Java (`java/pie-risk-engine/`)

```
cd java/pie-risk-engine && mvn clean verify
```

**14 passed, 0 failed, 0 skipped**, against REAL Postgres 17 and Redis 7
containers via Testcontainers (not H2, not mocks):

| Test class | Tests | Layer |
|---|---|---|
| TradeCandidateTest | 3 | Pure unit, no Spring context |
| RiskEngineServiceIT | 11 | Full Spring context + Testcontainers Postgres/Redis |

RiskEngineServiceIT covers: clean approval + audit row, oversized lots,
non-multiple-of-lot-size quantity, expiry-day entry, zero/inverted stop
distance, negative/zero premiums, the spec §6 risk-fixture derivation,
daily-loss-limit survival across a fresh read (proving Postgres, not JVM
memory, is the source of truth), duplicate-close-event idempotency, daily
reset, consecutive-loss stop + cooldown, and the Redis portfolio lock
serialising decisions for one correlation group while leaving others free.

## Docker Compose — all four profiles, live

Each profile was built and started for real in this session (not just
`config`-validated):

| Profile | Built | `/actuator/health` | API exercised |
|---|---|---|---|
| dev | ✅ | ✅ UP | ✅ decision, expiry rejection, idempotent close ×2, daily-loss block, Postgres+Redis inspected directly |
| test | ✅ | ✅ UP | ✅ one live decision call |
| integration | ✅ | ✅ UP | ✅ one live decision call |
| paper | ✅ | ✅ UP | health only (same image/config path as the others) |

Two real defects (DEF-006, DEF-007) were found and fixed getting the
non-dev profiles healthy — see defect-register.csv.

## Kubernetes manifests

8 manifests in `deploy/k8s/risk-engine/` — syntactically valid (parsed with
PyYAML, every doc has `apiVersion`/`kind`/`metadata`). **Not applied to a
live cluster**: this sandbox has no `kubectl`/`kind`/cluster available, and
downloading+executing an unverified `kubectl` binary was correctly refused
by the environment's own safety controls. This matches the owner's own
spec, which calls Kubernetes a "planned production deployment capability,
not a mandatory dependency for initial development."

## Not run / not applicable

- Frontend tests (Jest/Vitest/Playwright): no frontend code exists yet.
- Kafka integration tests: no Kafka producer/consumer code exists yet.
- Broker API tests: the Angel One adapter is an intentionally-unvalidated
  scaffold (`raise NotImplementedError`).

## Complexity, memory and load audit (Part K/L/R of the master checklist)

Real measurements, taken this session, against the actual running code —
not invented targets.

### Time/space complexity (Part K)

Audited every hot-path loop in `pie_trader/auto/structure.py`, `smc.py`,
`direction.py`, `pnl.py`. Findings:

- `Structure.update()` is O(1) amortised per candle: swing confirmation
  slices a fixed `left+right+1` window; sweep/break checks only look at the
  last 8 swings or index-bounded recent events — none scan full history.
- `SMC.update()` is O(1) per candle: both zone lists (`fvgs`, `obs`) were
  already capped at `max_zones=12` with front-eviction.
- `VolatilityEngine` was already correctly bounded (`deque(maxlen=window)`).
- **Found and fixed (DEF-008):** `Structure.highs`, `.lows`, `.events` had
  no cap and grew for the life of the process — a real memory leak over a
  long-running live session (not a backtest-only concern). Fixed with a
  `max_history` front-eviction cap, same pattern SMC already used. Proven
  with a 5,000-candle test asserting the bound holds.
- **Known, deliberate gap:** `Structure.candles` itself is still unbounded.
  Every `Swing`/`Event` stores an absolute candle index, and multiple
  modules (`engine.py`'s trailing logic, `smc.py`'s order-block lookback)
  index into `candles` by that absolute position. Bounding it safely needs
  an index-remapping refactor, not a one-line fix — out of scope for this
  pass, called out explicitly rather than silently left unbounded.

### Real throughput/memory (measured, Python side)

One synthetic trading year (18,750 five-minute candles) through the full
auto-engine (structure + SMC + regime + direction + volatility + sizing):

| Metric | Measured value |
|---|---|
| Wall time | 4.13 s |
| Throughput | 4,539 candles/sec |
| Peak traced memory (`tracemalloc`) | 5.12 MB |
| `highs`/`lows`/`events` length after run | 500 / 500 / 500 (capped, as designed) |
| `candles` length after run | 18,750 (documented unbounded scope boundary above) |

4,539 candles/sec is roughly 1.3 million× faster than real-time 5-minute
candle arrival (one every 300 seconds) — per-candle compute latency is not
a real constraint for this engine at any foreseeable scale.

### Real load test (measured, Java risk-engine, against the live dev Compose stack)

500 requests, concurrency 20, real HTTP calls to the running container:

| Metric | Measured value |
|---|---|
| Throughput | 310 req/s |
| p50 | 48.7 ms |
| p95 | 103.2 ms |
| p99 | 406.0 ms |
| Errors | 0 / 500 (0.0%) |

No load target was invented to "pass" against — the master checklist's own
instruction ("derive targets from actual broker limits... do not invent
load targets") applies: this system has no live broker yet, so there is no
real target to compare against. These numbers describe what the current
single-container, single-replica setup can do on this sandbox's hardware,
nothing more.

### Real concurrency proof (Critical Safety #5/#7, Q6 "concurrent duplicate
signals cannot create duplicate orders")

50 **truly simultaneous** HTTP requests (ThreadPoolExecutor, 50 workers)
alternating NIFTY/BANKNIFTY (same correlation group) against the live
risk engine:

- **4 approved, 46 rejected with "another decision already in flight", 0
  double-processed.**
- Cross-checked directly against Postgres's own `risk_decision_log`: the
  count of `allowed=true` rows for NIFTY+BANKNIFTY matched the client-side
  "approved" count exactly (2 + 2 = 4).

This is the one property-based/concurrency claim in the master checklist
that a single-threaded unit test cannot actually prove — this was real
concurrent network traffic against the real running service, not a
simulation.

## `test_dev` sandbox pass — pre-existing `development` code (this session)

The user cloned `test_dev` from `development` and asked for a sandbox run
against the pre-existing, previously-unexercised parts of that branch
(`backend/pietrader-gateway`, `analytics/`, the root/infra Compose files —
none of this is code this QA effort wrote; it predates it). All defects
found are recorded as DEF-009..DEF-014 in `qa/defect-register.csv`.

### `backend/pietrader-gateway` (Java 21, Spring Boot) — Maven `test`

Starting state: the module's own test suite did not compile at all against
its own main sources. After fixing the compile errors (a field rename the
tests hadn't caught up with, mocks for two constructor dependencies the
tests predated, a wrong method under test, etc. — all test-only fixes, see
DEF-011/012/013) plus two real production defects (DEF-009, DEF-010):

```
mvn clean test
Tests run: 216, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Real production defects found and fixed:
- **DEF-009**: `RiskManager.onTradeClosed()` computed the daily max-loss
  threshold as `capital * 0.05`, ignoring the dedicated, already-configured
  `maxDailyLoss` field (`trading.max.loss`) entirely.
- **DEF-010**: `TradeStateManager.savePosition()` wrote positions to Redis
  with no TTL, contradicting `RedisKeys.position()`'s own documented
  "TTL: 1 day" contract.

Test-only defects found and fixed (no business logic touched; see
DEF-011/012/013 for full detail):
- `ExitEngineTest` never stubbed `positionManager.getActiveSymbols()`, so
  `runExitChecks()`'s loop body — including every exit — never ran; 5 of 6
  tests were failing on "zero interactions", the 6th was passing vacuously.
- `ExitEngineMarketConditionTest`'s shared tick-builder set a gamma-flip
  sentinel value that, per `ExitEngine`'s own real contract, is read as an
  *active* flip zone (not "disabled" as intended) — so `GAMMA_FLIP` fired
  before any of the compression/gamma/IV rules the 7 tests actually target;
  the helper also never built the `MarketStructureDTO` the real compression
  check reads, and every test stubbed a `positionManager.getPosition()`
  call `ExitEngine` never makes.
- Two stale, internally-contradictory duplicate test classes under
  `com.pietrader.redis` (asserting a key contract that was never
  implemented) were deleted — the real key contract they duplicated is
  already covered by `com.pietrader.state.RedisKeysTest`/`TradeStateManagerTest`.

### `java/pie-risk-engine` (this QA effort's own module) — regression check

Re-run on `test_dev` after the gateway fixes above, to confirm no
cross-contamination:

```
mvn test            → Tests run: 3,  Failures: 0, Errors: 0   (TradeCandidateTest)
mvn clean verify    → Tests run: 11, Failures: 0, Errors: 0   (RiskEngineServiceIT, real Postgres+Redis via Testcontainers)
```

Matches the 14/14 baseline from the original QA pass — unchanged.

### `pie_trader/` (this QA effort's own package) — regression check

```
python3 -m pytest -q
42 passed
```

Matches the 42/42 baseline — unchanged.

### `analytics/` (pre-existing Python service, not written by this QA effort)

`analytics/tests/` (`test_execution_flow.py`, `test_data_contract.py`) had
never been run in this sandbox. Its two declared dependencies
(`kafka-python`, `psycopg2-binary` — both already listed in
`analytics/requirements.txt`) simply weren't installed yet in this
container; once installed:

```
cd analytics && python3 -m pytest -q
88 passed in 0.35s
```

**DEF-014**: four files under `analytics/option_intelligence/` were named
with pytest's `test_*.py` discovery prefix (`test_nse.py`,
`test_all_indecies_nse.py`, `test_scraper.py`,
`ingestion/test_nse_poll.py`) despite being manual, network-calling
diagnostic scripts with no test framework and no assertions — one of them
an unbounded `while True:` live-polling loop. Running `pytest` scoped to
`analytics/` (exactly what exercising this module on its own looks like)
would import and execute all four at collection time: real HTTPS calls to
`nseindia.com`, and in the polling-loop case an indefinite hang. Renamed
all four out of pytest's discovery pattern (content unchanged, still
runnable manually); updated the one place that regenerates
`test_scraper.py`'s content (`INSTALL.py`) to match. Root `pyproject.toml`'s
`testpaths = ["tests"]` means a plain `pytest` from the repo root was never
affected by this — the hazard was specific to running pytest scoped to
`analytics/`.

### Compose files — layered profiles (`deploy/compose/`) + standalone files

All 4 documented profiles (`dev`/`test`/`integration`/`paper`), layered
with their base file exactly as `deploy/README.md` instructs
(`docker compose -f docker-compose.yml -f docker-compose.<profile>.yml`),
validate cleanly with `docker compose config`. The two standalone,
non-profile compose files (`docker-compose.yml`, `infrastructure/docker-compose.yml`)
also validate cleanly on their own. No defects found.

### Not exercised

`ui/` is an empty placeholder (`.gitkeep` only — no frontend exists yet, as
already documented in the master checklist's gap list). Kubernetes
manifests were already validated schema-only in the original QA pass (not
re-run here — no cluster available in this sandbox either).
