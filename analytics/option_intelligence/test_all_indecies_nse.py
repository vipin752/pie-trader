import requests
import urllib3
import time

urllib3.disable_warnings()

BASE = "https://www.nseindia.com"
OPTION_PAGE = "https://www.nseindia.com/option-chain"

API = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol={symbol}&expiry={expiry}"

HEADERS = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
    "Referer": OPTION_PAGE
}

EXPIRY_MAP = {
    "NIFTY": "17-Mar-2026",
    "BANKNIFTY": "30-Mar-2026",
    "MIDCPNIFTY": "30-Mar-2026",
    "FINNIFTY": "30-Mar-2026"
}

session = requests.Session()
session.verify = False
session.headers.update(HEADERS)

print("Warmup NSE")

session.get(BASE)
time.sleep(2)

session.get(OPTION_PAGE)
time.sleep(2)

for symbol, expiry in EXPIRY_MAP.items():

    print("\n====================")
    print("Testing:", symbol)
    print("Using expiry:", expiry)

    url = API.format(symbol=symbol, expiry=expiry)

    r = session.get(url)

    print("STATUS:", r.status_code)
    print("SIZE:", len(r.text))

    data = r.json()

    records = data.get("records", {})

    spot = records.get("underlyingValue", 0)
    rows = records.get("data", [])

    print("Spot:", spot)
    print("Number of strikes:", len(rows))

    if rows:
        ce = rows[0].get("CE", {})
        print("Sample strike:", ce.get("strikePrice"))
        