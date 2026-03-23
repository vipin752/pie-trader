# PIE Trader — Institutional Grade Options Trading System

PIE Trader is a rule-based + data-driven options trading system using:
- SMC / ICT concepts
- Options data (Gamma, PCR, IV, OI)
- Event-driven architecture (Kafka)
- Automated execution
- Trade journaling and performance analytics

---

## System Architecture

Market Data (Angel One WebSocket)
        ↓
Java Backend (Market Engine)
        ↓
Kafka
        ↓
Python Analytics (Intelligence Engine)
        ↓
Kafka
        ↓
Java Backend (Execution Engine)
        ↓
Redis (Live Positions)
        ↓
Postgres (Trade Journal & Stats)
        ↓
Next.js Frontend (Decision Terminal UI)

---

## Tech Stack

| Layer | Technology |
|------|------------|
| Market Engine | Java (Spring Boot) |
| Intelligence Engine | Python |
| Execution Engine | Java |
| Event Bus | Kafka |
| Cache | Redis |
| Database | Postgres |
| UI | Next.js |
| Infra | Docker |

---

## Kafka Topics

| Topic | Description |
|------|-------------|
| pie.market.state | Market state from Java |
| pie.analytics.results | Trade decision from Python |
| pie.trade.events | Executed trades |
| pie.position.events | Position updates |
| pie.alerts | Alerts |

---

## Run Infrastructure

```bash
docker-compose up -d
