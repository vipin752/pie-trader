import os

class Settings:
    # ── ANGEL ONE ──────────────────────────────────────────────────────────────
    ANGEL_API_KEY       = os.environ.get("ANGEL_API_KEY", "")
    ANGEL_CLIENT_ID     = os.environ.get("ANGEL_CLIENT_ID", "")
    ANGEL_PASSWORD      = os.environ.get("ANGEL_PASSWORD", "")
    ANGEL_TOTP_SECRET   = os.environ.get("ANGEL_TOTP_SECRET", "")
    ANGEL_BASE_URL      = "https://apiconnect.angelbroking.com"
    ANGEL_WS_URL        = "wss://smartapisocket.angelone.in/smart-stream"

    # ── KAFKA ──────────────────────────────────────────────────────────────────
    KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    KAFKA_TICK_TOPIC        = "pie.market.ticks"
    KAFKA_DECISION_TOPIC    = "pie.analytics.results"
    KAFKA_TRADE_EVENT_TOPIC = "pie.trade.events"
    KAFKA_POSITION_TOPIC    = "pie.position.events"
    KAFKA_CONSUMER_GROUP    = "pie-analytics-group"

    # ── POSTGRES ───────────────────────────────────────────────────────────────
    POSTGRES_URL = os.getenv(
        "POSTGRES_URL",
        "postgresql://pietrader:pietrader@2024@localhost:5432/pietrader"
    )

    # ── DATA MODE ──────────────────────────────────────────────────────────────
    # LIVE = use ticks from Java WS (pie.market.ticks)
    # NSE  = use NSE scraper (fallback / testing)
    DATA_MODE = os.getenv("DATA_MODE", "LIVE")

    # ── CONTRACT FILE ──────────────────────────────────────────────────────────
    # Path to local OpenAPIScripMaster.json (copy from Java resources)
    CONTRACT_FILE_PATH = os.getenv(
        "CONTRACT_FILE_PATH",
        "angel_feed/OpenAPIScripMaster.json"
    )
    # Fallback URL if local file not found
    CONTRACT_URL = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json"


# Backward compat
KAFKA_BOOTSTRAP_SERVERS = Settings.KAFKA_BOOTSTRAP_SERVERS
