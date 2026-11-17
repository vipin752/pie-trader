# Phase 1 — Trading Safety: Security & Execution Hardening

Per the master implementation prompt's phased plan (PHASE 1 — TRADING
SAFETY). Covers CR-001 (D, E, F — SSL, secrets, Kafka deserialization)
and CR-002 (GET endpoints triggering execution). CR-001.A/B/C (real
JWT/OIDC authentication, RBAC, service-to-service auth) is scoped out of
this phase — see "Deferred" below.

## A. Summary

Found and fixed one severe, confirmed MITM vulnerability (global SSL
bypass), one severe, confirmed credential-exposure incident (real broker
and infrastructure credentials committed to a public repo), and one
severe, confirmed defect where three different GET endpoints could
trigger a real (paper-mode) order attempt as a side effect of being
polled. All three were speculative concerns in the governing prompt;
all three were verified real by reading the actual source before
touching anything, per Phase 0's mandatory process.

## B. Repository and branch

`vipin752/pie-trader`, branch `feature/security-execution-hardening`,
based on `development`.

## C. Base commit

`713036a` (merge of PR #10 into `development`).

## D. Feature branch

`feature/security-execution-hardening`

## E. Files added

- `backend/pietrader-gateway/.env.example`
- `infrastructure/.env.example`
- `backend/pietrader-gateway/src/test/java/com/pietrader/service/impl/OptionServiceImplTest.java`

## F. Files modified

- `backend/pietrader-gateway/src/main/java/com/pietrader/PietraderGatewayApplication.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/config/SslConfig.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/broker/angel/AngelWebSocketClient.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/broker/angel/AngelTokenService.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/service/OptionService.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/service/impl/OptionServiceImpl.java`
- `backend/pietrader-gateway/src/main/java/com/pietrader/controller/OptionController.java`
- `backend/pietrader-gateway/src/main/resources/application.properties`
- `backend/pietrader-gateway/src/test/java/com/pietrader/config/SslConfigTest.java` (rewritten)
- `infrastructure/docker-compose.yml`
- `qa/defect-register.csv` (DEF-015..018)

## G. Files removed

- `backend/pietrader-gateway/src/main/java/com/pietrader/broker/angel/SslBypass.java` (dead, unused)
- `backend/pietrader-gateway/src/main/java/com/pietrader/broker/angel/SslUtil.java` (dead, unused)

## H. Technical explanation

**DEF-015 (SSL bypass).** `PietraderGatewayApplication.main()` called
`SslConfig.applyGlobalSslBypass()` before Spring even started, installing
a trust-all `TrustManager` and an always-true hostname verifier as the
**JVM-wide defaults** — every HTTPS connection the process makes, in
every environment, was unverified. A second call site did the same thing
from `AngelTokenService.loadFromUrl()`; `AngelWebSocketClient` had its own
identical inline copy for the WebSocket connection specifically (because
`java-websocket` doesn't pick up the JVM defaults); two further dead
duplicate classes (`SslBypass`, `SslUtil`) existed, unused. All bypass
code is now removed; standard JVM certificate verification applies
everywhere. If a genuine `PKIX path building failed` error recurs against
a real Angel One connection, the fix is a dedicated truststore with the
specific missing CA — documented in `SslConfig`'s class javadoc — never a
bypass.

**DEF-016 (committed secrets).** Confirmed real-looking credentials
committed to this **public** repository: a Postgres password, a Redis
password, and — in `application.properties` — an Angel One API key,
client ID, account password, TOTP secret, and registered local/public IP
+ MAC address (the client ID matches the account owner's own real P&L
statement shared earlier in this project). `infrastructure/docker-compose.yml`
separately hardcoded a second, different Angel One API key, a TOTP
secret, and a JWT signing secret with an unenforced "change in
production" comment. All have been present since the commit that added
the `ui/` folder — pre-existing, not introduced by this work. Every
hardcoded value has been removed and replaced with a required (no
fallback) environment variable; `infrastructure/docker-compose.yml` now
uses `${VAR:?message}` so compose refuses to start rather than silently
falling back to a real value. **This is a forward fix only** — see
Section K.

**DEF-017 (GET triggering execution).** `OptionServiceImpl.getOptionSummary()`
was reachable from three GET endpoints and unconditionally called
`TradeSignalService.process()` and `ExecutionService.execute()` — the
latter's own javadoc says "called by Kafka consumer," confirming it was
never meant to be reachable from a read path. Split into a pure read
(`getOptionSummary`, used by all three existing GET endpoints
unchanged) and an explicit command (`triggerAnalyticsCycle`, used only by
a new `POST /api/options/trigger`). The Kafka `AnalyticsConsumer` path —
the actual intended automated execution pipeline — is untouched.

**DEF-018 (Kafka trusted packages).** `spring.json.trusted.packages` was
`*`. Restricted to `com.pietrader.*`.

## I. API changes

- `POST /api/options/trigger?symbol=` — new. Explicit command path for
  what `GET /api/options/summary` used to do as a side effect.
- `GET /api/options/summary` — behavior change: no longer triggers
  execution or persists a signal. Response shape (the raw Python
  analytics JSON) is unchanged for the success case.
- No other endpoint's request/response contract changed.

## J. Security impact

Removes a process-wide MITM vulnerability (DEF-015) and an unsafe Kafka
deserialization configuration (DEF-018). Forward-fixes a real credential
exposure (DEF-016) in the current file state. Does not implement real
JWT/OIDC authentication, RBAC, or service-to-service auth (CR-001.A/B/C)
— see "Deferred."

## K. Financial behaviour impact

None of the Section 15 non-negotiable items (lot sizing, capital
allocation, stop loss, daily loss, max trades, risk percentages, position
sizing, cooldown, strategy parameters) were touched. DEF-017's fix changes
*when* execution can be triggered via HTTP (no longer from a GET), not
*how* execution decides anything once triggered — every order still passes
through the exact same `ExecutionGate`/`RiskManager` path, Kafka-triggered
or now-explicitly-POST-triggered.

## L–O. Tests executed / passed / failed / skipped

```
cd backend/pietrader-gateway && mvn clean test
Tests run: 223, Failures: 0, Errors: 0, Skipped: 0 -- BUILD SUCCESS
(baseline before this phase: 220/220 — 5 removed with the old
SslConfigTest, 2 added back with the new one, 5 added in
OptionServiceImplTest = +3 net)

cd .. && python3 -m pytest -q
42 passed (unchanged from baseline)

cd infrastructure && docker compose config
- Fails closed with "required variable ... is missing a value: Set ..."
  when ANGELONE_API_KEY/ANGELONE_CLIENT_ID/ANGELONE_TOTP_SECRET/
  JWT_SECRET are unset — confirmed.
- Parses cleanly when all required vars are set to test placeholder
  values — confirmed.
```

Not run in this phase: `java/pie-risk-engine`'s Testcontainers suite
(unaffected by any change in this PR — no file in that module was
touched) and any live network test against Angel One's real endpoints
(this sandbox has no network path to those hosts).

## P. Known limitations

- DEF-016's fix is forward-only. The real exposed values remain in git
  history. The account owner was told directly in this session and has
  chosen to rotate credentials on their own timeline rather than now;
  git history was explicitly **not** rewritten (destructive, needs
  separate explicit approval, not requested).
- DEF-015's fix could not be verified against a real Angel One
  connection (no network access to those hosts from this sandbox) — the
  fix is correct by inspection (standard JVM verification replacing a
  bypass) but a live-connection check is NOT VERIFIED.
- CR-001.A/B/C (real JWT/OIDC auth) is **not started** — see below.

## Q. Approval gates

None reached. Nothing in this phase touches a Section 15 financial
parameter. Phase 2 (Risk Authority Review) is the next approval gate and
has **not** been started — per the master prompt's own instruction, it
requires a full execution call-graph/risk-rule inventory deliverable and
an explicit stop for approval before any financial-rule change, which is
separate work from this PR.

## R. PR links

Opened against `vipin752/pie-trader`'s `development` branch — see the
chat response accompanying this report for the live link (never
fabricated here; confirmed via the GitHub API before being reported as
created).

## S. Rollback instructions

`git revert` the PR's merge commit, or `git checkout development --
<path>` the specific files listed in F/G above. No database migration,
no schema change, no config value removed that wasn't already required
to be supplied externally (the fail-fast behavior means a correctly
configured deployment is unaffected; only a deployment that was silently
relying on a committed default needs its `.env` updated — see the new
`.env.example` files).

## T. Next phase

Phase 2 — Risk Authority Review (CR-003): map every risk implementation
and execution call path, compare configuration sources, and produce the
design-gate deliverable the master prompt requires — explicitly a STOP
point for the account owner's approval before any financial-rule change,
not an implementation phase.

## Deferred (not part of this PR, and why)

**CR-001.A/B/C — real JWT/OIDC authentication, RBAC, service-to-service
auth.** This needs an actual identity provider decision (a real OIDC
issuer — Keycloak, Auth0, Google Workspace, or similar) that doesn't
exist yet in this project. Turning on `spring-boot-starter-oauth2-resource-server`
validation against no real issuer would make every endpoint
unreachable, not secure. This is an infrastructure decision for the
account owner to make, not one to invent — flagged for a separate,
explicit follow-up rather than guessed at here.
