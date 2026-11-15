import requests
import urllib3

urllib3.disable_warnings()

BASE_URL = "https://www.nseindia.com"

API_URL = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry=24-Mar-2026"

HEADERS = {
 "User-Agent": "Mozilla/5.0",
 "Accept": "*/*",
 "Referer": "https://www.nseindia.com/option-chain"
}


class NSEFetcher:

    def __init__(self):

        self.session = requests.Session()

        self.session.get(BASE_URL, headers=HEADERS, verify=False)

    def fetch(self):

        r = self.session.get(API_URL, headers=HEADERS, verify=False)

        data = r.json()

        if "records" not in data:
            return None

        rows = []

        for row in data["records"]["data"]:

            ce = row.get("CE", {})
            pe = row.get("PE", {})

            rows.append({
                "strike": row["strikePrice"],
                "call_oi": ce.get("openInterest", 0),
                "put_oi": pe.get("openInterest", 0)
            })

        return data["records"]["underlyingValue"], rows
        