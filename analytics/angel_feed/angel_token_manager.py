import time
import pyotp
import requests
from config.settings import Settings
from utils.logger import get_logger

logger = get_logger(__name__)

class AngelTokenManager:

    def __init__(self):
        self.jwt_token = None
        self.refresh_token = None
        self.feed_token = None
        self.last_login_time = 0

    def generate_totp(self):
        totp = pyotp.TOTP(Settings.ANGEL_TOTP_SECRET)
        return totp.now()

    def login(self):
        logger.info("🔐 Angel Login Started")

        url = f"{Settings.ANGEL_BASE_URL}/rest/auth/angelbroking/user/v1/loginByPassword"

        payload = {
            "clientcode": Settings.ANGEL_CLIENT_ID,
            "password": Settings.ANGEL_PASSWORD,
            "totp": self.generate_totp(),
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

            logger.info("✅ Angel Login Success")
        else:
            raise Exception(f"Angel login failed: {data}")

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
            logger.info("✅ JWT Refreshed")
        else:
            logger.error("❌ JWT Refresh failed, re-login required")
            self.login()

    def get_valid_tokens(self):
        # JWT expires ~24 hours → refresh every 12 hours
        if not self.jwt_token:
            self.login()

        elif time.time() - self.last_login_time > 12 * 60 * 60:
            self.refresh_jwt()

        return {
            "jwt": self.jwt_token,
            "refresh": self.refresh_token,
            "feed": self.feed_token
        }
        