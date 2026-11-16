import sys, json, logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
sys.path.insert(0, ".")
from option_intelligence.ingestion.nse_option_scraper import NSEOptionScraper

print("\nFetching NIFTY option chain...\n")
data = NSEOptionScraper().fetch("NIFTY")
if data is None:
    print("FAILED — check network / VPN (NSE may block non-Indian IPs)")
    sys.exit(1)
print(f"SUCCESS  spot={data['spot']}  strikes={len(data['strikes'])}  expiries={data['expiry_dates'][:2]}")
print(json.dumps(data["strikes"][0], indent=2))
