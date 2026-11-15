import sys, time
sys.path.insert(0, ".")
import requests, urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
from datetime import date, timedelta

def nearest_expiry():
    today = date.today()
    days_ahead = (3 - today.weekday()) % 7
    expiry = today + timedelta(days=days_ahead)
    return expiry.strftime("%d-%b-%Y")

HEADERS = {
    "User-Agent":       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept":           "application/json, text/plain, */*",
    "Connection":       "keep-alive",
    "DNT":              "1",
    "Cache-Control":    "no-cache",
    "Referer":          "https://www.nseindia.com/option-chain",
    "X-Requested-With": "XMLHttpRequest",
}

s = requests.Session()
s.headers.update(HEADERS)
s.verify = False

expiry = nearest_expiry()
print(f"\n  Nearest expiry detected: {expiry}")
print("="*60)

print("\n[1] Warmup NSE home...")
r = s.get("https://www.nseindia.com", timeout=15, verify=False)
print(f"    {r.status_code}  cookies={list(s.cookies.keys())}")
time.sleep(2)

print("\n[2] Warmup option-chain page...")
r = s.get("https://www.nseindia.com/option-chain", timeout=15, verify=False)
print(f"    {r.status_code}  cookies={list(s.cookies.keys())}")
time.sleep(2)

print("\n[3] Testing confirmed URL (with expiry)...")
url = f"https://www.nseindia.com/api/option-chain-v3?type=Indices&symbol=NIFTY&expiry={expiry}"
print(f"    {url}")
r = s.get(url, timeout=20, verify=False)
print(f"    HTTP {r.status_code}  bytes={len(r.content)}")
if r.status_code == 200:
    data = r.json()
    if "records" in data:
        spot    = data["records"].get("underlyingValue")
        strikes = len(data["records"].get("data", []))
        print(f"    spot={spot}  strikes={strikes}")
        print(f"    RESULT: SUCCESS ✅")
    else:
        print(f"    RESULT: Bad JSON — keys={list(data.keys())}")
else:
    print(f"    RESULT: FAIL — body={r.text[:200]}")

print("\n" + "="*60 + "\n")
