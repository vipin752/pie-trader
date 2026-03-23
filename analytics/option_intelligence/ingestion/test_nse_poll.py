import time
from option_intelligence.ingestion.scraper.nse_option_scraper import NSEOptionScraper

scraper = NSEOptionScraper()

def analyze(rows):

    call_total = sum(r["call_oi"] for r in rows)
    put_total = sum(r["put_oi"] for r in rows)

    call_wall = max(rows, key=lambda x: x["call_oi"])
    put_wall = max(rows, key=lambda x: x["put_oi"])

    bias = "NEUTRAL"

    if put_total > call_total:
        bias = "BULLISH"
    elif call_total > put_total:
        bias = "BEARISH"

    return {
        "call_total_oi": call_total,
        "put_total_oi": put_total,
        "call_wall": call_wall["strike"],
        "put_wall": put_wall["strike"],
        "bias": bias
    }


while True:

    try:

        result = scraper.fetch()

        if result:

            spot, rows = result

            signal = analyze(rows)

            print("\n----- OPTION SNAPSHOT -----")
            print("Spot:", spot)
            print(signal)

    except Exception as e:
        print("Error:", e)

    time.sleep(15)
    