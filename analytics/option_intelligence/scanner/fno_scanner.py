from concurrent.futures import ThreadPoolExecutor, as_completed
from option_intelligence.service.option_service import OptionService


class FNOMarketScanner:

    def __init__(self):

        self.service = OptionService()

        self.symbols = [
            "NIFTY",
            "BANKNIFTY",
            "FINNIFTY",
            "MIDCPNIFTY"
        ]

    def scan(self):

        results = {}

        with ThreadPoolExecutor(max_workers=8) as executor:

            futures = {
                executor.submit(self.service.get_option_summary, symbol): symbol
                for symbol in self.symbols
            }

            for future in as_completed(futures):

                symbol = futures[future]

                try:

                    results[symbol] = future.result()

                except Exception as e:

                    results[symbol] = {
                        "error": str(e)
                    }

        return results
        