import time
import json
import requests
from utils.logger import get_logger
from config.settings import Settings

logger = get_logger(__name__)

TOKEN_FILE = "angel_session.json"


class AngelSessionManager:

    def __init__(self):
        self.jwt_token = None
        self.refresh_token = None
        self.feed_token = None
        self.last_login_time = 0
        self.load_session()

    # ------------------------------------------------
    # LOAD SESSION FROM FILE
    # ------------------------------------------------
    def load_session(self):
        try:
            with open(TOKEN_FILE, "r") as f:
                data = json.load(f)
                self.jwt_token = data["jwt"]
                self.refresh_token = data["refresh"]
                self.feed_token = data["feed"]
                self.last_login_time = data["login_time"]
                logger.info("✅ Loaded existing Angel session")
        except:
            logger.info("⚠ No previous session found")

    # ------------------------------------------------
    # SAVE SESSION
    # ------------------------------------------------
    def save_session(self):
        data = {
            "jwt": self.jwt_token,
            "refresh": self.refresh_token,
            "feed": self.feed_token,
            "login_time": self.last_login_time
        }
        with open(TOKEN_FILE, "w") as f:
            json.dump(data, f)

    # ------------------------------------------------
    # LOGIN WITH TOTP (MANUAL INPUT)
    # ------------------------------------------------
    def login(self):
        logger.info("🔐 Angel Login Required")

        totp = input("👉 Enter Angel TOTP: ")

        url = f"{Settings.ANGEL_BASE_URL}/rest/auth/angelbroking/user/v1/loginByPassword"

        payload = {
            "clientcode": Settings.ANGEL_CLIENT_ID,
            "password": Settings.ANGEL_PASSWORD,
            "totp": totp,
            "state": "LIVE"
        }

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-UserType": "USER",
            "X-SourceID": "WEB",
            "X-PrivateKey": Settings.ANGEL_API_KEY
        }

        response = requests.post(url, json=payload, headers=headers)
        data = response.json()

        if data.get("status"):
            tokens = data["data"]
            self.jwt_token = tokens["jwtToken"]
            self.refresh_token = tokens["refreshToken"]
            self.feed_token = tokens["feedToken"]
            self.last_login_time = time.time()
            self.save_session()

            logger.info("✅ Angel Login Success")
        else:
            raise Exception(f"Angel login failed: {data}")

    # ------------------------------------------------
    # REFRESH JWT
    # ------------------------------------------------
    def refresh_jwt(self):
        logger.info("🔄 Refreshing JWT Token")

        url = f"{Settings.ANGEL_BASE_URL}/rest/auth/angelbroking/jwt/v1/generateTokens"

        headers = {
            "Authorization": f"Bearer {self.jwt_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-UserType": "USER",
            "X-SourceID": "WEB",
            "X-PrivateKey": Settings.ANGEL_API_KEY
        }

        response = requests.post(url, headers=headers)
        data = response.json()

        if data.get("status"):
            tokens = data["data"]
            self.jwt_token = tokens["jwtToken"]
            self.refresh_token = tokens["refreshToken"]
            self.save_session()
            logger.info("✅ JWT Refreshed")
        else:
            logger.error("❌ Refresh failed → Login again")
            self.login()

    # ------------------------------------------------
    # SESSION MAINTENANCE LOOP
    # ------------------------------------------------
    def maintain_session(self):
        while True:
            try:
                # Refresh every 20 hours
                if time.time() - self.last_login_time > 20 * 60 * 60:
                    self.refresh_jwt()

                time.sleep(300)  # Check every 5 minutes

            except Exception as e:
                logger.error(f"Session maintenance error: {e}")
                time.sleep(60)

    # ------------------------------------------------
    # GET TOKENS
    # ------------------------------------------------
    def get_tokens(self):
        if not self.jwt_token:
            self.login()

        return {
            "jwt": self.jwt_token,
            "refresh": self.refresh_token,
            "feed": self.feed_token
        }
        