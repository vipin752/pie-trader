# Keycloak JWT Integration — Gateway Authorization

Closes CR-001.A/B/C (deferred out of Phase 1: "real JWT/OIDC
authentication, RBAC, service-to-service auth") with a real Keycloak
resource-server integration, replacing `SecurityConfig`'s
`anyRequest().permitAll()` placeholder (left there with a "tighten with
JWT in Phase 7" comment since Phase 1) with a route-by-route
authorization matrix built from this gateway's actual controllers.

## A. Summary

Implemented Spring Security OAuth2 resource-server JWT validation
against Keycloak (issuer `https://pietrader.in/realms/pie-trader` —
**verify the exact realm path against your deployment's own OIDC
discovery document before deploying; see Known Limitations**), a
`permissions`-claim-to-authority converter, and a full authorization
matrix covering every real `@GetMapping`/`@PostMapping` in the gateway —
not the illustrative route list from the integration brief, which missed
`/api/broker/**`, `/api/signals/**`, and `/api/system/**` entirely.
While verifying the real route list, found two pre-existing, unrelated
findings (DEF-019, DEF-020 — see below) and mitigated both at the
authorization layer without touching the underlying business logic they
flag, per the master prompt's financial-safety boundaries.

## B. Repository and branch

`vipin752/pie-trader`, branch `feature/keycloak-jwt-integration`, based
on `development`.

## C. Base commit

`dc2f55a` (merge of PR #12 into `development`).

## D. Feature branch

`feature/keycloak-jwt-integration`

## E. Files added

- `backend/pietrader-gateway/src/main/java/com/pietrader/config/PermissionsAuthoritiesConverter.java`
- `backend/pietrader-gateway/src/test/java/com/pietrader/config/PermissionsAuthoritiesConverterTest.java`
- `backend/pietrader-gateway/src/test/java/com/pietrader/config/JwtDecoderValidationTest.java`
- `backend/pietrader-gateway/src/test/java/com/pietrader/config/SecurityConfigTest.java`
- `qa/keycloak-jwt-integration-report.md` (this file)

## F. Files modified

- `backend/pietrader-gateway/src/main/java/com/pietrader/config/SecurityConfig.java` (rewritten)
- `backend/pietrader-gateway/src/main/resources/application.properties`
- `backend/pietrader-gateway/src/test/resources/application-test.properties`
- `backend/pietrader-gateway/.env.example`
- `infrastructure/docker-compose.yml`, `infrastructure/.env.example`
- `docker-compose.yml`, `.env.example` (root)
- `qa/defect-register.csv` (DEF-019, DEF-020)

## G. Files removed

None. (The unused `JWT_SECRET` env var was removed from the three
docker-compose files above, but it was never a file of its own — see
Technical explanation.)

## H. Technical explanation

**Real route inventory, not the integration brief's assumed one.**
Before writing any authorization rule, every `@GetMapping`/`@PostMapping`
in `backend/pietrader-gateway/src/main/java/com/pietrader/**` was
enumerated directly (`grep -rn "@RequestMapping\|@GetMapping\|..."`).
The user-provided integration brief's own route list was a reasonable
starting point for the dashboard/execution endpoints but missed real
controllers entirely: `AngelLoginController`/`AngelAuthController`
(`/api/broker/**` — broker session login, status, and Angel's own OAuth
redirect callback) and `TradeSingnalController` (`/api/signals/**` —
five read-only signal endpoints). The final matrix is built from the
real inventory, not the brief.

**`/api/broker/angel/redirect` is public, by necessity, not oversight.**
This is the page Angel One's own OAuth flow redirects the user's browser
to directly — it never carries our Bearer token, since the browser (not
our own frontend) navigates there. It does nothing sensitive itself (it
logs `code`/`state` and shows a message; the real token exchange is a
`TODO` not yet implemented), so leaving it `permitAll()` does not widen
the attack surface.

**`PERM_` authority prefix.** `PermissionsAuthoritiesConverter` reads the
access token's `permissions` claim (array form, as Keycloak's "User
Client Role" mapper emits by default; a delimited-string form is also
accepted defensively) and emits `PERM_<permission>` authorities, so
`SecurityConfig` gates routes with `hasAuthority("PERM_trader.read")`
etc.

**Two pre-existing findings surfaced while building the matrix (not
introduced by this PR) — see `qa/defect-register.csv` for full detail:**

- **DEF-019.** `GET /api/options/test-kafka/{symbol}` is a GET request
  with a real execution-pipeline side effect (forces Python analytics to
  run and publish to Kafka, which `AnalyticsConsumer` picks up and feeds
  into `TradeSignalService.process()` → `ExecutionService.execute()`) —
  the same category of issue CR-002/DEF-017 fixed for
  `/api/options/summary`. **Mitigated, not fixed**: gated behind
  `PERM_trader.ops` now; still a GET with a side effect underneath.
  Recommend converting it to a POST command like `/api/options/trigger`
  in a future change.
- **DEF-020.** `POST /api/mode` accepts `mode=LIVE` with **no code-level
  block at all** — this directly concerns the master prompt's absolute,
  non-negotiable "never enable live trading" rule. **Mitigated, not
  fixed**: now requires `PERM_trader.admin`, closing the "anyone at all"
  exposure Phase 0 would have found if it had looked at this route. The
  ability for an admin-permissioned caller to set `LIVE` at all is
  **unchanged** and is explicitly **not addressed here** — that is Phase
  2 / Risk Authority Review territory per the master prompt's own
  mandatory approval gates, not an authentication change. Flagging this
  clearly rather than silently fixing or silently ignoring it.

**Unused `JWT_SECRET` removed.** Searched the whole repository
(`grep -rn "JWT_SECRET"`) before removing it: it was set as a container
env var in three docker-compose files but **never read by any
application code** (no `${JWT_SECRET}` reference anywhere in
`application.properties`). A resource server validating Keycloak-signed
JWTs fetches the realm's public signing keys from its own JWKS endpoint
and needs no shared secret at all, so this was genuinely dead
configuration, not a behavior change.

## I. API changes

No endpoint's URL, method, or response shape changed. What changed is
authorization: every route not explicitly listed as public now requires
a Bearer JWT with the matching `permissions` claim; previously
`anyRequest().permitAll()` required nothing at all.

## J. Security impact

Closes the gateway's complete absence of authentication/authorization
(previously every route — including trade execution, manual trade,
square-off, emergency unlock/reset, and mode switching — accepted any
request with no token whatsoever). Adds real signature/issuer/audience/
expiry validation via Spring Security's standard OAuth2 resource-server
machinery (not a custom, unverified scheme). Surfaces DEF-019 and
DEF-020 (above) rather than leaving them undiscovered.

## K. Financial behaviour impact

None. No risk parameter, execution logic, position-sizing rule, or
trading-mode business logic was changed. `POST /api/mode` can still
accept `mode=LIVE` exactly as before — only *who* can reach that route
changed (see DEF-020; that capability itself is explicitly out of scope
for this PR and flagged for Phase 2 Risk Authority Review).

## L–O. Tests executed / passed / failed / skipped

- `mvn test` (full suite, `backend/pietrader-gateway`): **338/338
  passing** (223 baseline from Phase 1 + 105 `SecurityConfigTest` + 5
  `JwtDecoderValidationTest` + 5 `PermissionsAuthoritiesConverterTest`).
  Zero failures, zero errors, zero skipped.
- `SecurityConfigTest` proves the full authorization matrix against
  every real route (enumerated from the controllers themselves, mirrored
  1:1 by a test-only stub controller so these tests never touch
  Postgres, Redis, Kafka, Angel One, or shared mutable trading state):
  no token → 401; the route's required authority → not blocked; an
  unrelated authority → 403; `/test/redis/**` denied even with every
  known authority; a validly-signed token carrying zero permissions →
  403 on a protected route; the two genuinely public routes → not
  blocked.
- `JwtDecoderValidationTest` proves **real** signature, issuer,
  audience, and expiry validation using a locally generated RSA key pair
  and Spring Security's actual `NimbusJwtDecoder` +
  `JwtValidators.createDefaultWithIssuer(...)` — not a mock. Covers:
  correctly signed + right issuer + right audience + not expired →
  decodes; wrong signing key → rejected; wrong issuer → rejected; wrong/
  missing audience → rejected; expired → rejected.
- `PermissionsAuthoritiesConverterTest` proves the `permissions` claim →
  `PERM_` authority mapping directly (array form, delimited-string
  fallback, missing claim, blank entries, duplicates).

## P. Known limitations (NOT VERIFIED)

- **The exact issuer URL could not be verified against a live Keycloak
  instance from this sandbox.** This session's outbound network policy
  does not allow `pietrader.in`, so `https://pietrader.in/.well-known/
  openid-configuration` and `https://pietrader.in/realms/pie-trader/
  .well-known/openid-configuration` could not be fetched to confirm the
  realm's own `issuer` field matches the value configured in
  `JWT_ISSUER_URI`. **This must be verified by you** before deploying —
  see `backend/pietrader-gateway/.env.example`'s own comment on this.
- **No live round-trip against a real Keycloak-issued token.** All JWT
  validation is proven against a locally generated RSA key pair
  (`JwtDecoderValidationTest`), not an actual token minted by your
  deployed realm. The `permissions` claim shape assumed (array of
  strings from a "User Client Role" mapper) should be double-checked
  against a real token once Keycloak is configured per this PR's
  instructions.
- **No Docker daemon available in this sandbox** — the full gateway
  (which needs Postgres, Redis, and Kafka to boot) was not started as a
  live HTTP server here; `mvn test` is what's actually been run.
- DEF-019 and DEF-020 (above) are mitigated at the authorization layer
  only, not fixed at the root cause — both are explicitly flagged, not
  silently left for someone to rediscover later.

## Q. Approval gates

None reached. This PR is authentication/authorization only — Phase 2
(Risk Authority Review, CR-003) has **not** been started and must not be
without your explicit sign-off, per the master prompt's own mandatory
approval gates. DEF-020 specifically needs that gate before its
underlying capability (not just who can reach it) is addressed.

## R. PR links

See the pull request opened from `feature/keycloak-jwt-integration`
against `development`.

## S. Rollback instructions

Revert this PR's merge commit. `SecurityConfig` will return to
`anyRequest().permitAll()` (Phase 1's state) — no database migration, no
data to roll back, no running process to restart beyond the gateway
itself.

## T. Next phase

Verify the real issuer URL against your Keycloak deployment (Known
Limitations, above), create the `pie-trader-api`/`pie-trader-web`
Keycloak clients and role-to-group assignments as described in this PR,
and test one real login end to end. Separately, and only with your
explicit sign-off: Phase 2 Risk Authority Review should address DEF-020
(should `LIVE` even be a reachable value for `/api/mode`, and under what
additional control) and, lower priority, DEF-019 (converting
`/api/options/test-kafka/{symbol}` to a POST command).
