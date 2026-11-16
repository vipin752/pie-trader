import requests
import urllib3
import json
import time

urllib3.disable_warnings()

BASE = "https://www.nseindia.com"
OPTION_PAGE = "https://www.nseindia.com/option-chain"
API = "https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry=17-Mar-2026"

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "Accept": "application/json",
    "Referer": OPTION_PAGE
}

session = requests.Session()
session.verify = False
session.headers.update(headers)

print("Step 1: Load NSE homepage")
session.get(BASE)

time.sleep(2)

print("Step 2: Load option chain page")
session.get(OPTION_PAGE)

time.sleep(2)

print("Step 3: Call API")

r = session.get(API)

print("STATUS:", r.status_code)
print("LENGTH:", len(r.text))

print("FIRST 500 CHARS:")
print(r.text[:500])
