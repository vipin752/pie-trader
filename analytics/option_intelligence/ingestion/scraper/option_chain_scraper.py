import requests
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class OptionChainScraper:

    BASE_URL = "https://www.nseindia.com"
    API_URL = "https://www.nseindia.com/api/option-chain-indices?symbol=NIFTY"

    HEADERS = {
        "User-Agent": "Mozilla/5.0",
        "Accept-Language": "en-US,en;q=0.9"
    }

    def __init__(self):

        self.session = requests.Session()

        # NSE requires a session cookie
        self.session.get(
            self.BASE_URL,
            headers=self.HEADERS,
            verify=False
        )

    def fetch(self):

        response = self.session.get(
            self.API_URL,
            headers=self.HEADERS,
            verify=False
        )

        data = response.json()

        strikes = []

        for record in data["records"]["data"]:

            strike = record["strikePrice"]

            ce = record.get("CE", {})
            pe = record.get("PE", {})

            strikes.append({
                "strike": strike,
                "call_oi": ce.get("openInterest", 0),
                "call_change_oi": ce.get("changeinOpenInterest", 0),
                "put_oi": pe.get("openInterest", 0),
                "put_change_oi": pe.get("changeinOpenInterest", 0)
            })

        return strikes
        