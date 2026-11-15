# PIE TRADER — Analytics Engine v4.0

## Architecture
```
Java WebSocket → pie.market.ticks (Kafka)
                          ↓
               Python: build option chain
                          ↓
               OptionEngine: 80+ intelligence engines
                          ↓
               DecisionPublisher → pie.analytics.results (Kafka)
                          ↓
               Java TradingOrchestrator → execution
                          ↓
               Java → pie.position.events (Kafka)
                          ↓
               Python: suppress duplicate signals
```

## Quick Start
```bash
cd analytics
pip install -r requirements.txt

# Copy contract master from Java (one-time):
cp ../backend/src/main/resources/angel/OpenAPIScripMaster.json angel_feed/

# Start:
uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

## Data Mode
- **LIVE** (default): reads ticks from `pie.market.ticks` Kafka topic (published by Java Angel WebSocket)
- **NSE fallback**: used when live adapter not ready, fetches directly from NSE API

## Key Endpoints
- `GET /health` — service health + chain stats
- `GET /option-summary?symbol=NIFTY` — run analytics, get full OptionAnalyticsDTO
- `GET /live-option-chain` — current tick data in memory
- `GET /test-kafka?symbol=NIFTY` — force-publish one result to Kafka for Java testing
- `WS /ws/options` — stream analytics results (2s interval)
- `WS /ws/decision` — stream trade decisions (2s interval)
- `WS /ws/positions` — stream active positions from Java feedback (1s interval)

## Environment Variables
```
DATA_MODE=LIVE              # LIVE | NSE
CONTRACT_FILE_PATH=angel_feed/OpenAPIScripMaster.json
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
POSTGRES_URL=postgresql://pietrader:pietrader@2024@localhost:5432/pietrader
```
