import requests
import pandas as pd
from option_intelligence.engine.option_chain_parser import OptionChainParser
from option_intelligence.config.config_loader import ConfigLoader


class URLReader:

    def __init__(self):

        config = ConfigLoader()

        self.url = config.get("nse", "nse.option.chain.url")

        self.headers = {
            "User-Agent": "Mozilla/5.0"
        }

    def read(self):

        response = requests.get(self.url, headers=self.headers)

        data = response.json()

        records = data["records"]["data"]

        rows = []

        for r in records:

            strike = r["strikePrice"]

            ce = r.get("CE", {})
            pe = r.get("PE", {})

            rows.append({
                "strike": strike,
                "call_oi": ce.get("openInterest", 0),
                "call_change_oi": ce.get("changeinOpenInterest", 0),
                "put_oi": pe.get("openInterest", 0),
                "put_change_oi": pe.get("changeinOpenInterest", 0)
            })

        df = pd.DataFrame(rows)

        parser = OptionChainParser()

        return parser.parse(df)
        