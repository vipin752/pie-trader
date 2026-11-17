# 🥧 PIE TRADER
### Personal Institutional Engine
**Company : Neelkanth Trading**
**Domain  : pietrader.in**
**Package : com.pietrader**

---

> *"Trading is not gambling.*
> *Trading is a probabilistic discipline based on mathematics, data, and risk management."*

---

## 📌 About This Repository

This is the **private development repository** for PIE Trader — a personal institutional trading intelligence platform built and managed by **Vipin Dubey, Neelkanth Trading**.

This repository is **not for commercial distribution**.
This repository is **not for public use**.
All code, design, and documentation is proprietary.

---

## 🌿 Branch Strategy

| Branch      | Purpose                                        | Push Policy              |
|-------------|------------------------------------------------|--------------------------|
| `main`      | Stable, tested, production-ready code only     | Merge from develop only  |
| `develop`   | Active development — all feature work goes here | Direct push allowed      |
| `feature/*` | Individual feature branches                    | Merge into develop only  |
| `hotfix/*`  | Critical fixes                                 | Merge into develop + main|

**Rule: No code goes directly to `main` until it is fully tested on `develop`.**

---

## 🎯 What is PIE Trader?

PIE Trader is a **personal trading operating system** that combines four intelligence layers into one platform:

### Layer 1 — Market Intelligence
Analyzes where institutional money is positioned.
- Liquidity mapping (monthly / weekly / daily levels)
- Options flow (call wall, put wall, gamma flip, GEX)
- Sector momentum detection at 9:20 AM
- Market regime classification (trending / range / high-vol)
- Futures open interest positioning

### Layer 2 — Consistent Winning Rate
Mathematically validated signal engine targeting 55%+ win rate.

```
Consistent Win Rate = Edge (Signal Quality)
                    + Execution (Discipline)
                    − Behavior Drag (Psychology Errors)
```

Every signal receives a **Quality Score (0–100)**.
Trades are only recommended at score ≥ 70.

### Layer 3 — Trader Intelligence
Tracks and improves trader behavior using measurable rules.
- Behavior pattern detection (revenge trading, overtrading, etc.)
- Discipline score (0–100 daily)
- Pre-market ritual (5-step, 2 minutes)
- Process quality rating per trade
- Weekly review with system-generated suggestions

### Layer 4 — Execution Automation
Assisted trade execution through broker API.
- Angel One SmartAPI (Phase 1 broker)
- Manual confirmation required for all orders (Phase 1–5)
- Risk guardrails enforced at execution layer
- Idempotency keys on all orders (no duplicate execution)

---

## 🏗️ System Architecture

```
     React 19 + Vite Dashboard, served by its own Express BFF (ui/)
                      │
                      │ REST + WebSocket
                      ▼
           API Gateway (Spring Boot 3.4.1, Keycloak JWT secured)
                      │
       ┌──────────────┼──────────────┐
       │              │              │
  Market Service  Trader Service  Execution Service
       │              │              │
       └──────────────┼──────────────┘
                   Kafka Bus
                      │
             Python Analytics (FastAPI)
              ├── Sector Engine
              ├── Liquidity Engine
              ├── Gamma Engine
              ├── Probability Engine
              └── Behavior Engine
                      │
              PostgreSQL + Redis
```

**Communication Rule:**
Java services → Kafka → Python analytics.
Python → Kafka → Java (results).
Java NEVER calls Python directly.
Core NEVER calls broker API directly — always via `BrokerAdapter` interface.

---

## 🛠️ Technology Stack

| Layer            | Technology                  | Version  |
|------------------|-----------------------------|----------|
| Backend (gateway)| Java + Spring Boot          | 21 / 3.4.1 |
| Backend (risk engine) | Java + Spring Boot (separate module, `java/pie-risk-engine`) | 25 / 4.1.2 |
| Analytics Engine | Python + FastAPI            | 3.11     |
| Message Bus      | Apache Kafka                | — (see `infrastructure/docker-compose.yml`) |
| Frontend         | React + Vite, served by its own Express BFF (`ui/`) — not Next.js | 19 |
| Auth             | Keycloak (OAuth2/OIDC, JWT resource server) | — |
| Primary Database | PostgreSQL                  | 16       |
| Cache            | Redis                       | 7        |
| Broker           | Angel One SmartAPI          | v1       |
| Containerization | Docker + Docker Compose     | latest   |
| Build Tool       | Maven (Java), npm (`ui/`), pip (`analytics/`) | — |

---

## 📦 Repository Structure

The structure below is the real layout, verified against this checkout —
not the original planning blueprint (which described a multi-module
`pietrader-*` Maven layout and a Next.js `frontend/` that were never
actually built this way):

```
pie-trader/
│
├── backend/
│   └── pietrader-gateway/             ← The one Java Spring Boot module:
│                                          REST APIs, Keycloak JWT resource
│                                          server, broker integration,
│                                          Kafka producer/consumer
│
├── java/
│   └── pie-risk-engine/               ← Separate Java Spring Boot module —
│                                          stateless risk/ledger service,
│                                          its own Postgres/Redis,
│                                          deploy/k8s/risk-engine/ manifests
│
├── analytics/                         ← Python FastAPI intelligence engine
│   ├── app.py                         ← uvicorn entrypoint
│   ├── option_intelligence/
│   ├── decision_engine/
│   ├── market_data/
│   └── kafka_utils/
│
├── pie_trader/                        ← Python FIFO P&L / trade-report CLI
│   ├── pnl.py                         ← build_episodes() — FIFO matching
│   ├── detectors.py, rules.py, auto/  ← market-structure (SMC) detection
│   └── cli.py
│
├── ui/                                ← React 19 + Vite SPA, served by its
│   │                                     own Express BFF (server.ts) — NOT
│   │                                     Next.js, no frontend/ directory
│   ├── server.ts                      ← Express BFF entrypoint
│   ├── server/
│   │   ├── auth/authService.ts        ← Email/TOTP + OAuth2/OIDC (incl. Keycloak)
│   │   ├── connectors/backendBridge.ts← Calls to the Java gateway
│   │   └── ai/                        ← Gemini/OwnGenAi + PIE GenAI Platform bridge
│   └── src/                           ← React app (components/, data/, engine/, types/)
│
├── infrastructure/                    ← Docker Compose for Postgres, Redis,
│   ├── docker-compose.yml                Kafka, the gateway, analytics, ui/
│   └── .env.example
│
├── deploy/
│   ├── compose/                       ← Separate Compose profiles
│   │                                     (dev/test/integration/paper) for
│   │                                     java/pie-risk-engine specifically
│   ├── k8s/risk-engine/                ← K8s manifests for pie-risk-engine —
│   │                                     readiness, not yet applied to a
│   │                                     real cluster; see deploy/README.md
│   └── README.md
│
├── docs/
│   ├── LOCAL_SETUP.md                 ← Start here for local setup
│   ├── ARCHITECTURE.md
│   └── PLAYBOOK.md
│
├── qa/                                 ← Defect register, test reports,
│                                          per-phase delivery reports
│
├── tests/                             ← Python tests for pie_trader/
├── test-data/                         ← Sample broker statements, fixtures
├── design/                            ← Design tokens, shared CSS
│
├── .env.example                       ← Root Docker Compose env template
└── README1.md                         ← This file
```

---

## 🗺️ Development Phases

| Phase | Timeline     | Deliverables                                                         | Status      |
|-------|--------------|----------------------------------------------------------------------|-------------|
| **1** | Months 1–3   | Auth, data ingestion, PostgreSQL schema, Kafka, trade journal, Angel One adapter | 🚧 ACTIVE  |
| **2** | Months 3–5   | Sector momentum engine, option chain analytics, liquidity mapping, market regime | PLANNED    |
| **3** | Months 5–7   | Probability engine, signal engine (3 strategies), backtesting framework          | PLANNED    |
| **4** | Months 7–9   | Behavior analytics, discipline score, pre-market ritual, weekly review           | PLANNED    |
| **5** | Months 9–12  | 2nd broker (Upstox), assisted execution, OCR upload, alert automation            | PLANNED    |
| **6** | Year 2+      | Crypto lab, commodity engine, AI model, multi-user, revenue model                | 🔜 WIP     |

**Golden Rule: Phase N must be 100% complete before Phase N+1 begins.**

---

## 🏦 Broker Integration — Angel One SmartAPI

Phase 1 broker: **Angel One SmartAPI**

Features used in Phase 1:
- TOTP-based session authentication
- Live market data (WebSocket streaming)
- Historical OHLCV candles
- Option chain pull
- Order placement (Phase 5 — assisted only)

SmartAPI documentation: [https://smartapi.angelbroking.com/docs](https://smartapi.angelbroking.com/docs)

**Architecture rule:**
All broker calls go through the `BrokerAdapter` interface.
`AngelOneAdapter` implements this interface.
When a second broker is added (Upstox in Phase 5),
it gets its own adapter class.
Core system changes zero lines of code.

---

## 📐 Java Package Structure

```
com.pietrader
 ├── gateway        ← API gateway
 ├── auth           ← Authentication
 ├── market         ← Market data
 ├── options        ← Option analytics
 ├── execution      ← Broker adapter
 ├── trader         ← Trade journal
 ├── alerts         ← WebSocket alerts
 └── common         ← Shared utilities
```

---

## 🔐 Security Rules

- JWT access tokens with refresh token rotation
- All broker API keys encrypted at rest (AES-256)
- Secrets loaded from environment variables only — never hardcoded
- `.env` file is in `.gitignore` — never committed
- API rate limiting at gateway level
- Full audit trail via Kafka event log

---

## ⚙️ Local Development Setup

**Full step-by-step guide: [`docs/LOCAL_SETUP.md`](docs/LOCAL_SETUP.md)**
— covers the gateway, a local Keycloak (now required — the gateway is a
real JWT resource server), `ui/`, `analytics/`, and the companion
`pie-genai-platform` repo. Short version below.

### Prerequisites
- Docker (Desktop or Engine) + Docker Compose
- Java 21 (JDK) + Maven 3.9+
- Python 3.11+
- Node.js 22 (20+ also works)

### Start infrastructure only (recommended for Day 1)
```bash
cd infrastructure
cp .env.example .env   # fill in real values first
docker compose up -d postgres redis kafka zookeeper kafka-ui pgadmin
```

### Service URLs (local)
| Service         | URL                             | Credentials                        |
|-----------------|---------------------------------|------------------------------------|
| pgAdmin         | http://localhost:5050           | `admin@pietrader.in` / your own `PGADMIN_DEFAULT_PASSWORD` from `infrastructure/.env` — never a value committed here |
| Kafka UI        | http://localhost:8090           | No auth                            |
| API Gateway     | http://localhost:8080           | Keycloak JWT required on every route except `/`, `/health`, `/actuator/health` |
| Python Analytics| http://localhost:8000/docs (direct) or :8001 (via Compose) | No auth (internal) |
| `ui/` (React + Express BFF) | http://localhost:3000 | Email/TOTP or OAuth2/OIDC (incl. Keycloak) |
| Local Keycloak  | http://localhost:8180           | See `docs/LOCAL_SETUP.md` Step 2   |

### Environment setup
```bash
cp .env.example .env
# Add your Angel One credentials, or leave blank for a pure local-dev
# loop with angel.startup.enabled=false — see docs/LOCAL_SETUP.md.
```

---

## 📊 Database — Key Tables

| Table               | Purpose                                          |
|---------------------|--------------------------------------------------|
| `users`             | Trader profiles, risk settings                   |
| `broker_connections`| Encrypted Angel One credentials                  |
| `trading_sessions`  | Daily session + pre-market ritual data           |
| `trades`            | All trades with psychology + process tags        |
| `strategies`        | 5 defined strategies with regime constraints     |
| `market_data`       | OHLCV price data across timeframes               |
| `option_chain`      | Option chain snapshots with computed Greeks      |
| `sector_data`       | 9:20 AM sector momentum snapshots                |
| `signals`           | Generated signals with quality scores            |
| `behavior_metrics`  | Daily discipline scores and habit flags          |
| `market_events`     | Kafka event log — full audit trail               |
| `weekly_reviews`    | Sunday review records                            |
| `liquidity_levels`  | Daily computed liquidity + option key levels     |
| `refresh_tokens`    | JWT refresh token store                          |

---

## 🧠 Trader Behavior Intelligence

Six behavior patterns with **mathematically precise definitions**:

| Pattern               | Definition                                                           | Alert Level   |
|-----------------------|----------------------------------------------------------------------|---------------|
| Revenge Trading       | Loss → next trade < 15 min AND size > 1.3x previous                 | FRICTION      |
| Overtrading           | More than 3 completed trades in any 90-minute rolling window        | FRICTION      |
| Loss Magnification    | Avg loss (last 5 trades) > 1.5x avg profit (last 5 trades)         | SOFT          |
| Early Exit Winner     | Closed before 40% of target reached, not stopped out               | SOFT (logged) |
| Strategy Abandonment  | Signal score < 70 but trade placed                                  | SOFT          |
| Session Limit Breach  | Trade attempt beyond daily max set in pre-market ritual             | HARD BLOCK    |

**Three consequence levels:**
- **SOFT** → Alert banner shown. Trader proceeds.
- **FRICTION** → 5-minute mandatory pause with last 5 trades shown.
- **HARD BLOCK** → Order disabled. Typed reason required. Saved to journal.

---

## 🔮 Future Scope Modules (Phase 6+)

All future modules are built into the UI as **Work In Progress** cards.
Clicking them shows the full scope description.
None of these are in active development until Phase 1–5 is complete.

| Module                    | Phase |
|---------------------------|-------|
| Crypto Lab (BTC/ETH)      | 6     |
| Commodity Engine (MCX)    | 6     |
| USD/INR + FII Flow Engine | 6     |
| AI Decision Engine        | 6     |
| Cross-Asset Analysis      | 6     |
| Full Auto-Execution       | 6     |
| Multi-User Platform       | 6     |
| Signal Alert Service      | 6     |
| Fundamental Research      | 6     |
| DeFi Analytics            | 6     |

---

## 📋 Non-Negotiable Development Rules

1. No feature code on `main` — always develop on `develop` branch
2. Every algorithm defined mathematically on paper before coding
3. Every API endpoint in OpenAPI spec before implementation
4. Every DB change requires migration script — never alter schema directly
5. No analytics calculation in Java — Java orchestrates, Python calculates
6. No direct broker API calls — always through `BrokerAdapter`
7. No Java → Python direct calls — always via Kafka
8. All secrets in `.env` — never in code, never in logs
9. Every order has idempotency key — duplicate orders are a financial disaster
10. Phase 1–5: All trades require manual confirmation — no auto-execution

---

## 🏷️ Naming & Branding

| Item            | Value                              |
|-----------------|------------------------------------|
| Product Name    | PIE Trader                         |
| Company         | Neelkanth Trading (Numerology: 55) |
| Domain          | pietrader.in                       |
| Java Package    | com.pietrader                      |
| GitHub Repo     | pie-trader (Private)               |
| Primary Branch  | develop                            |
| Release Branch  | main                               |

---

## ⚠️ Compliance Notice

- Phase 1–5: PIE Trader is a **personal decision support tool**
- All execution requires **manual trader confirmation**
- This is **NOT algorithmic trading** (SEBI definition)
- Phase 6 auto-execution requires **SEBI algo registration** before activation
- Any signal sharing for a fee requires **SEBI Investment Adviser registration**
- This platform is for **personal use only** — not commercial distribution

---

## 📝 Document History

| Version | Date         | Author       | Notes                          |
|---------|--------------|--------------|--------------------------------|
| 1.0     | March 2026   | Vipin Dubey  | Initial commit — docs only     |

---

*PIE Trader — Personal Institutional Engine*
*© 2026 Neelkanth Trading | Vipin Dubey | pietrader.in*
*All rights reserved. Private use only.*
