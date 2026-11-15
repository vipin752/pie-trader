import pandas as pd
from models.option_chain_model import OptionChain, OptionStrike


class OptionChainParser:

    REQUIRED_COLUMNS = [
        "strike",
        "call_oi",
        "call_change_oi",
        "put_oi",
        "put_change_oi"
    ]

    COLUMN_MAP = {
        "STRIKE": "strike",
        "Strike Price": "strike",
        "strikePrice": "strike",

        "CALL OI": "call_oi",
        "openInterest": "call_oi",

        "CALL CHNG OI": "call_change_oi",
        "changeinOpenInterest": "call_change_oi",

        "PUT OI": "put_oi",
        "PUT CHNG OI": "put_change_oi"
    }

    def normalize_columns(self, df):

        new_columns = {}

        for col in df.columns:

            if col in self.COLUMN_MAP:
                new_columns[col] = self.COLUMN_MAP[col]

        df = df.rename(columns=new_columns)

        return df

    def parse(self, df):

        df = self.normalize_columns(df)

        strikes = []

        for _, row in df.iterrows():

            strike = OptionStrike(
                strike=row["strike"],
                call_oi=row.get("call_oi", 0),
                call_change_oi=row.get("call_change_oi", 0),
                call_volume=row.get("call_volume", 0),
                put_oi=row.get("put_oi", 0),
                put_change_oi=row.get("put_change_oi", 0),
                put_volume=row.get("put_volume", 0)
            )

            strikes.append(strike)

        return OptionChain(
            symbol="NIFTY",
            timestamp="manual",
            strikes=strikes
        )
        
        