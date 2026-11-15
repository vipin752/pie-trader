import requests
import pyotp
import time
from config.settings import Settings

class AngelAuthService:

    def __init__(self):
        self.jwt_token = None
        self.refresh_token = None
        self.feed_token = None
        self.last_login_time = 0

    def _generate_totp(self):
        return pyotp.TOTP(Settings.ANGEL_TOTP_SECRET).now()

    def login(self):
        url = f"{Settings.ANGEL_BASE_URL}/rest/auth/angelbroking/user/v1/loginByPassword"

        payload = {
            "clientcode": Settings.ANGEL_CLIENT_ID,
            "password": Settings.ANGEL_PASSWORD,
            "totp": self._generate_totp()
        }

        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-PrivateKey": Settings.ANGEL_API_KEY
        }

        response = requests.post(url, json=payload, headers=headers, timeout=10)
        data = response.json()

        if not data["status"]:
            raise Exception(f"Angel Login Failed: {data}")

        self.jwt_token = data["data"]["jwtToken"]
        self.refresh_token = data["data"]["refreshToken"]
        self.feed_token = data["data"]["feedToken"]
        self.last_login_time = time.time()

        return self.jwt_token, self.feed_token

    def get_tokens(self):
        # Refresh every 50 minutes
        if time.time() - self.last_login_time > 3000:
            return self.login()
        return self.jwt_token, self.feed_token
    