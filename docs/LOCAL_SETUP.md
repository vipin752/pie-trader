# Local Setup — PIE Trader + PIE GenAI Platform

This is the real, step-by-step path to running the whole system on your
own machine: `backend/pietrader-gateway` (Java), `analytics/` (Python),
`ui/` (React + Vite, served by its own Express BFF), a local Keycloak
for auth, and the companion
[`pie-genai-platform`](https://github.com/vipin752/pie-genai-platform)
repository.

**Kubernetes is not part of this.** If you've seen `deploy/k8s/` and
wondered whether you need it to run things locally — you don't. See
[Kubernetes — not part of local setup](#kubernetes--not-part-of-local-setup)
at the bottom.

## 0. Prerequisites

| Tool | Version used/verified in this repo |
|---|---|
| Docker + Docker Compose | any recent version (`docker compose version`) |
| Java (JDK) | 21 — `backend/pietrader-gateway/pom.xml`'s `<java.version>` |
| Maven | 3.9+ |
| Node.js | 22 (also works down to 20+; `ui/package.json` sets no floor) |
| Python | 3.11+ (`analytics/`), 3.11+ (`pie-genai-platform`) |
| `openssl` | for generating random secrets below |

Clone both repositories side by side:

```bash
git clone https://github.com/vipin752/pie-trader.git
git clone https://github.com/vipin752/pie-genai-platform.git
```

Everything below assumes you're in `pie-trader/` unless stated otherwise.

## 1. Start core infrastructure (Postgres, Redis, Kafka)

```bash
cd infrastructure
cp .env.example .env
```

Fill in `.env` — generate real random values, never reuse an example:

```bash
# inside infrastructure/.env
openssl rand -base64 24   # use the output for POSTGRES_PASSWORD
openssl rand -base64 24   # REDIS_PASSWORD
openssl rand -base64 24   # PGADMIN_DEFAULT_PASSWORD
openssl rand -base64 32   # PIE_SESSION_SECRET (ui/'s own session signing key)
```

Leave `ANGELONE_*` blank for now if you're not testing broker connectivity
yet — the gateway's `angel.startup.enabled` flag (see its own
`application.properties`) can be turned off for a pure local-dev loop.
Leave `JWT_ISSUER_URI`/`JWT_AUDIENCE` for now — Step 2 sets those up for
real, from a Keycloak instance you're about to start.

Start just the infra services first (recommended — faster inner loop
than rebuilding every container on every code change):

```bash
docker compose up -d postgres redis kafka zookeeper kafka-ui pgadmin
```

Verify:

```bash
curl -s localhost:5432 2>&1 | head -1     # connection refused w/ a Postgres-shaped error is fine — it's not HTTP
open http://localhost:8090                 # Kafka UI
open http://localhost:5050                 # pgAdmin (admin@pietrader.in / your PGADMIN_DEFAULT_PASSWORD)
```

## 2. Run a local Keycloak and configure it

`backend/pietrader-gateway` is now a real OAuth2 resource server — it
needs a real issuer to validate tokens against, even for local dev.

```bash
docker run -d --name pie-trader-keycloak -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26.0 start-dev
```

Open `http://localhost:8180`, log in as `admin`/`admin`, and:

1. **Create a realm** named `pie-trader`.
2. **Create the API client** (what the gateway validates tokens against):
   - Clients → Create client → Client ID: `pie-trader-api`
   - Client authentication: **Off**, Standard flow: **Off** (this client
     represents the protected API, not something that logs in)
   - Under this client's **Roles** tab, create: `trader.read`,
     `trader.execute`, `trader.manual`, `trader.squareoff`,
     `trader.admin`, `trader.ops` — these map exactly to
     `backend/pietrader-gateway`'s `SecurityConfig` authorities (see
     `qa/keycloak-jwt-integration-report.md`).
   - Create a realm group per role (or reuse one group with multiple
     roles for local testing) and assign your own user to it under
     **Users → (your user) → Role mapping**, filtering by client
     `pie-trader-api`.
3. **Create the web client** (what `ui/`'s Express BFF uses — see
   `ui/.env.example`'s own notes on why this must be confidential, not
   the public SPA client a pure-browser flow would use):
   - Clients → Create client → Client ID: `pie-trader-web`
   - Client authentication: **On** (confidential — `ui/server.ts`
     exchanges the code server-side and can hold a secret safely)
   - Standard flow: **On**
   - Valid redirect URIs: `http://localhost:3000/api/auth/oauth/oidc/callback`
   - Valid post logout redirect URIs: `http://localhost:3000/*`
   - Web origins: `http://localhost:3000`
   - Save, then copy the **Client secret** from the Credentials tab.
4. **Add the audience mapper** so `pie-trader-web`'s tokens are accepted
   by `pie-trader-api`:
   - `pie-trader-web` client → Client scopes → `pie-trader-web-dedicated`
     → Add mapper → By configuration → Audience
   - Included Client Audience: `pie-trader-api`
   - Add to access token: **On**
5. **Add the permissions mapper** (the actual authorization claim):
   - Same dedicated scope → Add mapper → By configuration → User Client Role
   - Client ID: `pie-trader-api`
   - Token Claim Name: `permissions`
   - Multivalued: **On**, Add to access token: **On**

At this point a real login against this realm produces a JWT with
`iss`, `aud: ["pie-trader-api"]`, and a `permissions` array — exactly
what `PermissionsAuthoritiesConverter` expects (see
`backend/pietrader-gateway/src/main/java/com/pietrader/config/`).

## 3. Configure and run the Java gateway

```bash
cd backend/pietrader-gateway
cp .env.example .env
```

Fill in `.env`:

```
DB_PASSWORD=<same value you put in infrastructure/.env>
REDIS_PASSWORD=<same>
JWT_ISSUER_URI=http://localhost:8180/realms/pie-trader
JWT_AUDIENCE=pie-trader-api
PIE_TRADER_FRONTEND_ORIGIN=http://localhost:3000
```

(`ANGELONE_*` can stay blank for a pure local-dev loop with
`angel.startup.enabled=false`.)

```bash
export $(grep -v '^#' .env | xargs)   # or use a tool like direnv/dotenv
mvn spring-boot:run
```

Verify:

```bash
curl localhost:8080/health          # {"status":"ok"} or similar, no auth needed
curl localhost:8080/actuator/health # public — status only, no component details
curl localhost:8080/api/dashboard   # 401 — no token yet, exactly as it should be
```

## 4. Run the Python analytics engine

```bash
cd analytics
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

(`PYTHON_ANALYTICS_URL` in the gateway's own config already defaults to
`http://localhost:8000`, matching this.) Verify: `curl localhost:8000/health`.

If you instead run it via `docker compose up -d python-analytics`
alongside Step 1's services, note it's mapped to host port **8001**
there (`infrastructure/docker-compose.yml`) — set
`PYTHON_ANALYTICS_URL=http://localhost:8001` in the gateway's `.env` to
match if you go that route.

## 5. Configure and run `ui/`

```bash
cd ui
npm install
cp .env.example .env
```

Fill in `.env`:

```
PIE_SESSION_SECRET=<same value you generated in Step 1>
CUSTOM_OIDC_NAME=Keycloak
CUSTOM_OIDC_CLIENT_ID=pie-trader-web
CUSTOM_OIDC_CLIENT_SECRET=<the client secret from Step 2.3>
CUSTOM_OIDC_AUTH_URL=http://localhost:8180/realms/pie-trader/protocol/openid-connect/auth
CUSTOM_OIDC_TOKEN_URL=http://localhost:8180/realms/pie-trader/protocol/openid-connect/token
CUSTOM_OIDC_USERINFO_URL=http://localhost:8180/realms/pie-trader/protocol/openid-connect/userinfo
```

```bash
npm run dev
```

Open `http://localhost:3000`. Verify:

```bash
curl localhost:3000/api/health
curl localhost:3000/api/auth/providers   # "oidc" should show configured: true
```

Sign in via the "Keycloak" option in the UI's login screen, or walk the
flow directly:

```bash
curl -s "localhost:3000/api/auth/oauth/oidc/url?riskProfile=Conservative&sessionMode=PAPER_DESK"
# open the returned "url" in a browser, log in, and the browser is
# redirected to the callback URL with a real code+state
```

After a successful login, `GET /api/auth/session` (with the session
token as header `x-pie-session`) reports `hasGatewayAccess: true` — that
means `ui/`'s BFF is holding a real Keycloak access token and will
forward it to the Java gateway as `Authorization: Bearer ...` on every
`/api/backend/*` call (see `server/auth/authService.ts`'s
`getGatewayAccessToken()` and `server/connectors/backendBridge.ts`).

## 6. Run `pie-genai-platform` (separate repo)

```bash
cd ../pie-genai-platform
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
```

Its default model provider is Ollama (self-hosted, no API key). If you
have [Ollama](https://ollama.com) installed locally:

```bash
ollama serve &
ollama pull llama3.1   # or whatever OLLAMA_MODEL you set in .env
```

Otherwise leave it running — the platform fails every model call
honestly (never a fabricated answer) rather than requiring Ollama to
exist just to boot.

```bash
pytest -v
uvicorn pie_genai.api.app:app --reload
```

Verify: `curl localhost:8000/api/v1/health`.

**Port clash note:** this platform's own default port (`:8000`) is the
same as `analytics/`'s. Run them on different ports locally — e.g.
`uvicorn pie_genai.api.app:app --reload --port 8010` — and set
`PIE_GENAI_PLATFORM_URL=http://localhost:8010` in `ui/.env` to match.

Back in `ui/.env`, set:

```
PIE_GENAI_PLATFORM_URL=http://localhost:8010
```

and restart `ui/`'s `npm run dev`. The "PIE GenAI Platform (Self-Hosted)"
option in the Experience Coach chat (and `GET /api/ai/platform-chat/health`)
now talks to this real service — see `server/ai/pieGenAiPlatformBridge.ts`.

## 7. End-to-end verification checklist

- [ ] `curl localhost:8080/actuator/health` → `200`, public, no token
- [ ] `curl localhost:8080/api/dashboard` → `401`, no token
- [ ] Log in via `ui/`'s Keycloak option, then `curl` the gateway's
      `/api/dashboard` with `Authorization: Bearer <token from Keycloak>`
      → `200` if your user has `trader.read` assigned (Step 2.2)
- [ ] `curl -X POST localhost:3000/api/backend/sync -H "x-pie-session: <token>" -H "Content-Type: application/json" -d '{"javaGatewayUrl":"http://localhost:8080","preferExternalData":true,"symbol":"NIFTY 50"}'`
      → `"usedExternal": true`, `"source": "java-gateway"`
- [ ] `curl localhost:8010/api/v1/health` → `{"status":"ok"}`

If any of these instead show a fallback to local data or a 401/403, that's
the system behaving correctly and honestly — not a bug — when a token,
permission, or an upstream service is missing. See each PR under
`qa/` (`qa/keycloak-jwt-integration-report.md` especially) for exactly
what's been proven end to end versus what still needs a live check.

## Kubernetes — not part of local setup

`deploy/k8s/risk-engine/` is a complete, schema-validated Kubernetes
manifest set (Namespace, ConfigMap, Secret template, Deployment,
Service, HPA, PodDisruptionBudget, NetworkPolicy) for a **different**
Java module — `java/pie-risk-engine` — not `backend/pietrader-gateway`
covered above. See `deploy/README.md`, which already says this plainly:

> **Kubernetes — readiness, not yet deployment.** These have been
> schema-validated... but never applied to a real cluster — no
> Kubernetes cluster exists in the environment these were written in.

In short:
- You do **not** need Kubernetes, `kubectl`, or a cluster to run
  anything described in this document.
- These manifests exist to prove the *shape* of a future production
  deployment for the risk engine is ready, not because local
  development needs them.
- `deploy/compose/` is the real way to run `java/pie-risk-engine`
  locally, documented in `deploy/README.md`, with the same
  Docker-Compose-first philosophy as this document.
- If you ever do apply these manifests to a real cluster, `deploy/README.md`
  lists exactly what to replace first (the image tag, the secrets, the
  DB/Redis hosts) — nothing here has been exercised against a live
  cluster.

## Known gaps in this setup (reported honestly, not hidden)

- **No live Keycloak verification was possible from the sandbox this
  guide's companion PRs were built in** — no outbound network access to
  any real Keycloak host. The steps above are accurate against
  Keycloak's own documented API and were exercised against a mock
  Keycloak standing in for the real thing (see
  `qa/keycloak-jwt-integration-report.md` and the PR for
  `fix/jwt-review-followups`) — but you should be the first to actually
  run Step 2 against a real Keycloak and confirm it end to end.
- `infrastructure/docker-compose.yml`'s `python-analytics` service had
  no `Dockerfile` until this change (added one) — if you were already
  running it a different way, nothing changes for you.
- `analytics/`'s own quick-start (`uvicorn app:app --port 8000`) and
  `infrastructure/docker-compose.yml`'s container port (`8001`) are
  genuinely two different defaults — not a typo, see the port-clash
  note in Step 4.
