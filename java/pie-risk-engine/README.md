# pie-risk-engine

The first Java module of PIE Trader, built against the stack decided for
production (spec §13 of `docs/ARCHITECTURE.md`): **Java 25 (LTS) + Spring
Boot 4.1.2**. It is a straight port of the deterministic half of
`pie_trader.rules.Guard` — the half the spec insists must stay Java no
matter what the Python research side does:

> "Java must remain responsible for deterministic execution and risk
> enforcement. Python analytics must not bypass the Java risk approval
> process." — spec §13

## What's real here vs what's a stand-in

**Real:** the build. `mvn test` and `mvn package` both run clean on Java
25.0.5 in this environment — this is proof the toolchain choice works, not
just a decision on paper. The risk rules themselves (daily loss limit,
consecutive-loss stop, lot caps including the minimum-position rejection,
expiry-day ban, per-trade risk ceiling, cooldown after a loss, day rollover)
are the same logic as the Python `Guard`, with 8 passing tests.

**Stand-in, not yet production:** state is `ConcurrentHashMap`-backed and
in-memory, single instance — the eventual version needs Postgres for the
audit trail (spec §9) and Redis for shared state across instances (spec
§13). There's no auth, no Kafka publish of `pie.risk.decisions`, and the
two rule tables (`rules.toml` and `RiskRules.defaults()`) are kept in sync
by hand, not from one source of truth yet.

## Run it

```bash
mvn spring-boot:run
# in another shell:
curl -X POST localhost:8080/api/risk/decision -H 'Content-Type: application/json' -d '{
  "underlying": "NIFTY", "symbol": "NIFTY 24 NOV 2026 25000 CE",
  "qty": 650, "lotSize": 65, "riskInr": 1500, "expiry": null, "now": "2026-11-16T10:00:00"
}'
```

## Why Java 25, and the rest of the stack it anchors

- **Java 25 LTS** (released Sept 2025, first LTS after 21) — installed and
  confirmed in this environment (`openjdk 25.0.5.0.3`). Run `apt-get install
  openjdk-25-jdk-headless` to match this environment exactly.
- **Spring Boot 4.1.2** — latest stable on Maven Central as of 2026-11-16
  (4.2.0 is still RC). Spring Boot 4 runs on Spring Framework 7 / Jakarta
  EE; fully supports Java 25.
- **Maven**, not Gradle, for this module: Gradle 8.14 (installed here) can
  target a Java 25 toolchain but running Gradle *itself* on 25 is the kind
  of cross-version friction not worth fighting before Phase 1 validation
  even exists. Maven invoked directly under `JAVA_HOME=java-25` has no such
  friction. Revisit if the team prefers Gradle once there are multiple
  modules worth a version catalog.
- See `docs/ARCHITECTURE.md` §13 for the rest of the stack (Kafka, Redis,
  Postgres, Next.js) and the Python/AI side, all re-grounded against this
  Java version.
