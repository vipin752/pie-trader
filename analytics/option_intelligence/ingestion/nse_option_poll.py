import requests
import time

BASE_URL = "https://www.nseindia.com"

API_URL = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol=NIFTY"

HEADERS = {
    "User-Agent": "Mozilla/5.0",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept": "application/json"
}


class NSEOptionPoller:

    def __init__(self):

        self.session = requests.Session()

        # NSE requires cookie handshake
        self.session.get(BASE_URL, headers=HEADERS, verify=False)

    def fetch(self):

        response = self.session.get(API_URL, headers=HEADERS, verify=False)

        data = response.json()

        records = data["records"]

        spot = records["underlyingValue"]

        rows = records["data"]

        return spot, rows


def process_chain(spot, rows):

    strikes = []

    for r in rows:

        strike = r["strikePrice"]

        ce = r.get("CE", {})
        pe = r.get("PE", {})

        strikes.append({
            "strike": strike,
            "call_oi": ce.get("openInterest", 0),
            "put_oi": pe.get("openInterest", 0)
        })

    strikes = sorted(strikes, key=lambda x: x["strike"])

    atm = min(strikes, key=lambda x: abs(x["strike"] - spot))["strike"]

    window = [
        s for s in strikes
        if atm - 500 <= s["strike"] <= atm + 500
    ]

    call_wall = max(window, key=lambda x: x["call_oi"])
    put_wall = max(window, key=lambda x: x["put_oi"])

    call_total = sum(x["call_oi"] for x in window)
    put_total = sum(x["put_oi"] for x in window)

    bias = "NEUTRAL"

    if put_total > call_total:
        bias = "BULLISH"

    elif call_total > put_total:
        bias = "BEARISH"

    return {
        "spot": spot,
        "call_wall": call_wall["strike"],
        "put_wall": put_wall["strike"],
        "call_total_oi": call_total,
        "put_total_oi": put_total,
        "bias": bias
    }


def start_polling():

    poller = NSEOptionPoller()

    while True:

        try:

            spot, rows = poller.fetch()

            result = process_chain(spot, rows)

            print(result)

        except Exception as e:

            print("Error:", e)

        time.sleep(15)
		