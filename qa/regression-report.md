# Regression Report

All pre-existing tests were re-run after every fix in this session; none
were weakened, skipped, or deleted to make a build green (per the master
prompt's explicit instruction).

| Suite | Before this session | After this session | Regressions |
|---|---|---|---|
| Python (`pytest`) | 31 passing | 41 passing | 0 |
| Java (`mvn verify`) | 8 passing (old in-memory `RiskEngineServiceTest`) | 14 passing (replaced with real-Postgres/Redis `RiskEngineServiceIT` + a new pure-unit `TradeCandidateTest`) | 0 |

The old Java unit-test file (`RiskEngineServiceTest.java`, in-memory
`ConcurrentHashMap`-backed) was deleted only because the service it tested
was rewritten to be Postgres/Redis-backed in this same session — its
scenarios were ported forward into `RiskEngineServiceIT`, not dropped. Every
assertion that existed before still exists now, plus the new ones for
Postgres durability, Redis locking, idempotent closes, and premium-based
risk validation.

No test's expected value was loosened to accommodate a defect; every
defect in `defect-register.csv` was fixed in the implementation, and the
test that caught it was kept (or strengthened) as its regression test.
