"""
Run this script, paste your cookie string from the Network tab, press Enter.
It saves nse_cookies.json automatically.

HOW TO GET THE COOKIE STRING:
  1. Open Chrome, press F12 FIRST
  2. Go to Network tab
  3. Visit: https://www.nseindia.com/option-chain
  4. Wait for it to load
  5. In Network tab filter box type: option-chain-v3
  6. Click the request (status 200)
  7. Click Headers -> Request Headers
  8. Find the "cookie:" line
  9. Copy everything AFTER "cookie: "
  10. Paste below when prompted
"""
import json, os, sys, requests, urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

ROOT        = os.path.dirname(os.path.abspath(__file__))
COOKIE_FILE = os.path.join(ROOT, "nse_cookies.json")

print("\n" + "="*60)
print("  NSE Cookie Setup")
print("="*60)
print("\nPaste your cookie string from Chrome Network tab")
print("(the full value after 'cookie: ' in Request Headers)")
print("\nPaste here and press Enter:")
print("-"*60)

raw = input().strip()

if not raw:
    print("Nothing pasted. Exiting.")
    sys.exit(1)

# parse "key=value; key2=value2; ..." into dict
cookies = {}
for part in raw.split(";"):
    part = part.strip()
    if "=" in part:
        k, _, v = part.partition("=")
        cookies[k.strip()] = v.strip()

print(f"\nParsed {len(cookies)} cookies: {list(cookies.keys())}")

# test immediately
from datetime import date, timedelta
today  = date.today()
days   = (3 - today.weekday()) % 7
expiry = (today + timedelta(days=days)).strftime("%d-%b-%Y")
url    = f"https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry={expiry}"

print(f"\nTesting against NSE API...")
print(f"URL: {url}")

s = requests.Session()
s.verify = False
s.headers.update({
    "User-Agent":       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept":           "application/json, text/plain, */*",
    "Referer":          "https://www.nseindia.com/option-chain",
    "X-Requested-With": "XMLHttpRequest",
})
for k, v in cookies.items():
    s.cookies.set(k, v, domain=".nseindia.com")

try:
    r = s.get(url, timeout=20, verify=False)
    print(f"HTTP {r.status_code}  bytes={len(r.content)}")

    if r.status_code == 200:
        data = r.json()
        if "records" in data:
            spot    = data["records"].get("underlyingValue")
            strikes = len(data["records"].get("data", []))
            print(f"Spot={spot}  Strikes={strikes}")
            # save cookies
            with open(COOKIE_FILE, "w") as f:
                json.dump(cookies, f, indent=2)
            print(f"\nSaved to {COOKIE_FILE}")
            print("\nSUCCESS! Now run:  python run.py\n")
        else:
            print(f"FAIL - response keys: {list(data.keys())}")
            print("Cookies may be wrong. Try again.")
    elif r.status_code == 401:
        print("FAIL - 401 Unauthorised. Cookies are invalid or expired.")
        print("Make sure you copy from a SUCCESSFUL request (status 200) in Network tab.")
    else:
        print(f"FAIL - HTTP {r.status_code}")
        print(r.text[:300])
except Exception as e:
    print(f"Error: {e}")
