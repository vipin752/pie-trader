import websocket
import json
import threading
from angel_feed.angel_token_manager import AngelTokenManager
from utils.logger import get_logger

logger = get_logger(__name__)

class AngelWebSocket:

    def __init__(self, on_tick_callback):
        self.token_manager = AngelTokenManager()
        self.ws = None
        self.on_tick_callback = on_tick_callback

    def connect(self):
        tokens = self.token_manager.get_valid_tokens()
        feed_token = tokens["feed"]

        ws_url = f"wss://smartapisocket.angelone.in/smart-stream?token={feed_token}"

        self.ws = websocket.WebSocketApp(
            ws_url,
            on_message=self.on_message,
            on_open=self.on_open,
            on_error=self.on_error,
            on_close=self.on_close
        )

        threading.Thread(target=self.ws.run_forever, daemon=True).start()

    def on_open(self, ws):
        logger.info("📡 Angel WebSocket Connected")
        self.subscribe()

    def subscribe(self):
        # Subscribe tokens here
        subscribe_message = {
            "action": 1,
            "params": {
                "mode": 3,
                "tokenList": [
                    {
                        "exchangeType": 2,
                        "tokens": ["26000"]  # NIFTY spot token example
                    }
                ]
            }
        }

        self.ws.send(json.dumps(subscribe_message))

    def on_message(self, ws, message):
        data = json.loads(message)
        self.on_tick_callback(data)

    def on_error(self, ws, error):
        logger.error(f"WebSocket error: {error}")

    def on_close(self, ws, close_status_code, close_msg):
        logger.warning("WebSocket closed → reconnecting")
        self.connect()
        