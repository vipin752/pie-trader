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
