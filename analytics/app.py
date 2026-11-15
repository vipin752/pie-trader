"""
PIE TRADER — app.py  v4.1  (Production Execution Engine)

Architecture:
    Angel Broker
         ↓
    Java (WS + REST)   — owns broker, session, tick feed
         ↓
    Kafka: pie.market.ticks        (raw ticks)
    Kafka: pie.market.state        (derived MarketState: gamma, IV, PCR, regime)
         ↓
    Python (this app)  — pure analytics, no broker dependency
         ↓
    Kafka: pie.analytics.results
         ↓
    Java Execution Engine
         ↓  (feedback)
    Kafka: pie.position.events  →  Python (position suppression)
    Kafka: pie.squareoff.signals ← Python (emergency exit)

Python responsibilities:
  1. Consume ticks from pie.market.ticks (Kafka)
  2. Consume MarketState from pie.market.state (Kafka)  ← GAP-1 FIX
  3. Build live option chain in memory
  4. Run OptionEngine analytics (all 80+ engines)
  5. Validate and publish decision to pie.analytics.results
  6. Consume pie.position.events (feedback from Java)
  7. Publish pie.squareoff.signals on emergency exit      ← GAP-5 FIX
  8. Persist analytics + regime + probability to Postgres
  9. Snapshot chain to Postgres every 15 min
  10. Expose REST + WebSocket for UI/monitoring
"""

from __future__ import annotations
import logging
import asyncio
import time
from contextlib import asynccontextmanager
from threading import Thread
from typing import Dict, Any

from fastapi import FastAPI, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

# ── Intelligence Engine (DO NOT MODIFY) ──────────────────────────────────────
from option_intelligence.scanner.fno_scanner import FNOMarketScanner
from option_intelligence.service.option_service import OptionService

# ── Live Market Infra ─────────────────────────────────────────────────────────
from angel_feed.token_mapper import TokenMapper
from angel_feed.angel_option_chain_adapter import AngelOptionChainAdapter
from kafka_utils.kafka_consumer import KafkaConsumerService
from kafka_utils.tick_processor import TickProcessor
from kafka_utils.position_event_consumer import PositionEventConsumer
from kafka_utils.topics import TICK_TOPIC, DECISION_TOPIC, POSITION_TOPIC
from market_data.live_option_chain import LiveOptionChain
from market_data.market_state_consumer import MarketStateConsumer   # GAP-1 FIX
from market_data.option_chain_snapshot import OptionChainSnapshotService
from storage.postgres_client import PostgresClient
from execution_bridge.decision_publisher import DecisionPublisher
from execution_bridge.squareoff_publisher import SquareoffPublisher  # GAP-5 FIX
from config.constants import SUPPORTED_SYMBOLS

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)

# ── Core Services (singletons) ────────────────────────────────────────────────
option_service    = OptionService()
scanner           = FNOMarketScanner()

token_mapper      = TokenMapper()
live_chain        = LiveOptionChain()
tick_processor    = TickProcessor()
live_adapter      = AngelOptionChainAdapter(live_chain, token_mapper)

position_consumer = PositionEventConsumer()
market_state_consumer = MarketStateConsumer()          # GAP-1 FIX
postgres_client   = PostgresClient()
snapshot_service  = OptionChainSnapshotService(live_chain, postgres_client)

# DecisionPublisher — primary Kafka publisher (sends FULL dict, not truncated)
decision_publisher  = DecisionPublisher(position_consumer=position_consumer)
# SquareoffPublisher — emergency exit signals to Java
squareoff_publisher = SquareoffPublisher()             # GAP-5 FIX

# Wire live adapter into OptionService
option_service.set_live_adapter(live_adapter)
# Wire MarketStateConsumer → OptionService + live_adapter (spot fallback)
option_service.set_market_state_consumer(market_state_consumer)
# Wire PostgresClient → OptionService._store_decision()
option_service.set_postgres_client(postgres_client)

# ── Background Thread Functions ───────────────────────────────────────────────

def start_tick_consumer():
    """
    Consumes pie.market.ticks (published by Java WebSocket adapter).
    Updates LiveOptionChain with each tick.
    """
    logger.info("▶ Starting tick consumer ← pie.market.ticks")
    consumer = KafkaConsumerService(TICK_TOPIC, group_id="pie-tick-consumer")

    def handle_tick(raw):
        try:
            tick = tick_processor.process(raw)
            if tick:
                live_chain.update_tick(tick)
        except Exception as e:
            logger.error(f"❌ Tick processing error: {e}")

    consumer.listen(handle_tick)


def start_snapshot_loop():
    """Saves option chain snapshot to Postgres every 15 minutes."""
    logger.info("▶ Starting snapshot service")
    while True:
        try:
            snapshot_service.run_snapshot_if_due()
        except Exception as e:
            logger.error(f"❌ Snapshot error: {e}")
        time.sleep(60)


def start_analytics_loop():
    """
    Main analytics + publish loop. Runs every 2 seconds for all symbols.

    Per cycle per symbol:
      1. Skip if Java has active position (no duplicate signals)
      2. Skip if chain not ready
      3. Run OptionEngine → result dict
      4. Check if emergency squareoff needed (risk breach)
      5. Persist to Postgres
      6. Publish to Kafka if action is PREPARE or EXECUTE
    """
    logger.info("▶ Starting analytics loop")
    while True:
        try:
            for symbol in SUPPORTED_SYMBOLS:
                # Gate 1: Skip if Java has an active position for this symbol
                if position_consumer.has_active_position(symbol):
                    logger.debug(f"⛔ {symbol}: Java has active position — skipping analytics")
                    continue

                # Gate 2: Skip if chain not ready yet
                if not live_adapter.is_ready(symbol):
                    logger.debug(f"⛔ {symbol}: chain not ready yet")
                    continue

                result = option_service.get_option_summary(symbol, publish=False)
                if not result:
                    continue

                # GAP-5 FIX: Check emergency squareoff conditions from result
                _check_emergency_squareoff(symbol, result)

                # Persist analytics to Postgres
                _persist_async(symbol, result)

                # Publish to Java execution engine
                decision_publisher.publish(result, symbol=symbol)

        except Exception as e:
            logger.error(f"❌ Analytics loop error: {e}")

        time.sleep(2)


def _check_emergency_squareoff(symbol: str, result: dict):
    """
    GAP-5 FIX: Publish squareoff signal if Python risk/exit engines
    detect an emergency condition while Java has an active position.

    Conditions:
      - risk_management.position == "EMERGENCY_EXIT"
      - exit_management.exit_plan has a forced exit signal
    """
    try:
        # Only relevant when Java has active position
        if not position_consumer.has_active_position(symbol):
            return

        risk = result.get("risk_management", {})
        exit_mgmt = result.get("exit_management", {})

        # Emergency risk breach
        if risk.get("position") == "EMERGENCY_EXIT":
            reason = risk.get("reason", "RISK_BREACH")
            logger.warning(f"🚨 [{symbol}] Emergency squareoff → {reason}")
            squareoff_publisher.squareoff(symbol, reason)
            return

        # Exit plan with force exit
        exit_plan = exit_mgmt.get("exit_plan")
        if exit_plan and exit_plan.get("force_exit") is True:
            reason = exit_plan.get("reason", "EXIT_ENGINE")
            logger.warning(f"🚨 [{symbol}] Exit engine squareoff → {reason}")
            squareoff_publisher.squareoff(symbol, reason)

    except Exception as e:
        logger.error(f"❌ Emergency squareoff check error [{symbol}]: {e}")


def _persist_async(symbol: str, result: dict):
    """Fire-and-forget Postgres writes — never block the analytics loop."""
    try:
        postgres_client.save_analytics_result(symbol, result)
    except Exception as e:
        logger.error(f"❌ analytics_result save failed for {symbol}: {e}")
    try:
        postgres_client.save_regime_log(symbol, result)
    except Exception as e:
        logger.error(f"❌ regime_log save failed for {symbol}: {e}")
    try:
        postgres_client.save_probability_log(symbol, result)
    except Exception as e:
        logger.error(f"❌ probability_log save failed for {symbol}: {e}")


def start_background_services():
    logger.info("🚀 Starting PIE Trader Analytics Services")

    # 0. GAP-1 FIX: MarketState consumer (pie.market.state from Java)
    #    Must start FIRST — feeds regime, gamma, IV, PCR to intelligence engines
    market_state_consumer.start()
    logger.info("✅ MarketStateConsumer started ← pie.market.state")

    # 1. Position feedback from Java (must start before analytics loop)
    position_consumer.start()

    # 2. Tick consumer (fills live chain from Java WebSocket)
    Thread(target=start_tick_consumer,  name="TickConsumer",  daemon=True).start()

    # 3. Snapshot loop (Postgres every 15 min)
    Thread(target=start_snapshot_loop,  name="SnapshotLoop",  daemon=True).start()

    # 4. Main analytics + publish loop
    Thread(target=start_analytics_loop, name="AnalyticsLoop", daemon=True).start()

    logger.info("✅ All background services started")


# ── FastAPI Lifespan ──────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    start_background_services()
    yield
    logger.info("🛑 Shutting down PIE Trader")
    decision_publisher.close()
    squareoff_publisher.close()   # GAP-5 FIX: graceful close


# ── FastAPI App ───────────────────────────────────────────────────────────────

app = FastAPI(
    title="PIE Trader — Analytics Engine",
    description="Pure analytics. Java owns broker. Kafka = nervous system.",
    version="4.1",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_credentials=True,
    allow_methods=["*"], allow_headers=["*"],
)


# ── REST Endpoints ────────────────────────────────────────────────────────────

@app.get("/")
def root():
    return {"service": "PIE Trader Analytics", "version": "4.1", "status": "UP"}


@app.get("/health")
def health():
    chain_stats = live_chain.stats()
    market_ready = {s: market_state_consumer.is_ready(s) for s in SUPPORTED_SYMBOLS}
    return {
        "status": "UP",
        "chain_stats": chain_stats,
        "market_state_ready": market_ready,
        "position_consumer": position_consumer.get_all(),
    }


@app.get("/option-summary")
def option_summary(symbol: str = Query(..., description="e.g. NIFTY")):
    try:
        symbol = symbol.upper()
        result = option_service.get_option_summary(symbol, publish=False)
        if not result:
            raise HTTPException(status_code=500, detail=f"No data for {symbol}")
        return result
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Option summary failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/scan-indices")
def scan_indices():
    results: Dict[str, Any] = {}
    for symbol in SUPPORTED_SYMBOLS:
        try:
            results[symbol] = option_service.get_option_summary(symbol, publish=False)
        except Exception as e:
            results[symbol] = {"error": str(e)}
    return results


@app.get("/scan-fno")
def scan_fno():
    try:
        return scanner.scan()
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/best-trade")
def best_trade():
    trades = []
    for symbol in SUPPORTED_SYMBOLS:
        try:
            data     = option_service.get_option_summary(symbol, publish=False)
            decision = data.get("auto_trade_decision", {})
            trades.append({"symbol": symbol, "decision": decision})
        except Exception as e:
            trades.append({"symbol": symbol, "error": str(e)})
    return {"best_trades": trades}


@app.get("/positions")
def positions():
    """Active positions tracked from Java feedback."""
    return position_consumer.get_all()


@app.get("/chain-stats")
def chain_stats():
    return live_chain.stats()


@app.get("/live-option-chain")
def live_option_chain_api():
    return live_chain.get_full_chain()


@app.get("/market-state/{symbol}")
def market_state(symbol: str):
    """Latest MarketState received from Java (pie.market.state)."""
    state = market_state_consumer.get(symbol.upper())
    if not state:
        return {"symbol": symbol.upper(), "status": "NOT_RECEIVED",
                "message": "No market state from Java yet. Check MarketStateKafkaProducer is running."}
    return state


@app.get("/test-kafka")
def test_kafka(symbol: str = Query(default="NIFTY")):
    """Force-publish one real analytics result to Kafka for Java integration testing."""
    try:
        symbol = symbol.upper()
        result = option_service.get_option_summary(symbol, publish=False)
        if not result:
            return {"error": "No data"}
        decision_publisher.publish(result, symbol=symbol)
        action = (result.get("auto_trade_decision", {})
                  .get("auto_trade_decision", {}).get("action", "?"))
        return {"status": "sent", "symbol": symbol, "action": action}
    except Exception as e:
        return {"error": str(e)}


@app.post("/emergency-squareoff/{symbol}")
def emergency_squareoff(symbol: str, reason: str = Query(default="MANUAL")):
    """Manually trigger squareoff signal to Java."""
    squareoff_publisher.squareoff(symbol.upper(), reason.upper())
    return {"status": "sent", "symbol": symbol.upper(), "reason": reason}


# ── WebSocket Endpoints ───────────────────────────────────────────────────────

@app.websocket("/ws/options")
async def ws_options(websocket: WebSocket):
    await websocket.accept()
    logger.info("🔌 /ws/options connected")
    try:
        while True:
            data = await asyncio.to_thread(
                option_service.get_option_summary, "NIFTY", False
            )
            await websocket.send_json(data)
            await asyncio.sleep(2)
    except WebSocketDisconnect:
        logger.info("🔌 /ws/options disconnected")


@app.websocket("/ws/decision")
async def ws_decision(websocket: WebSocket):
    await websocket.accept()
    logger.info("🔌 /ws/decision connected")
    try:
        while True:
            data = await asyncio.to_thread(
                option_service.get_option_summary, "NIFTY", False
            )
            await websocket.send_json(data)
            await asyncio.sleep(2)
    except WebSocketDisconnect:
        logger.info("🔌 /ws/decision disconnected")


@app.websocket("/ws/positions")
async def ws_positions(websocket: WebSocket):
    """Stream live position updates from Java feedback loop."""
    await websocket.accept()
    logger.info("🔌 /ws/positions connected")
    try:
        while True:
            await websocket.send_json(position_consumer.get_all())
            await asyncio.sleep(1)
    except WebSocketDisconnect:
        logger.info("🔌 /ws/positions disconnected")
        