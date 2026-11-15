import pandas as pd
from option_intelligence.engine.option_chain_parser import OptionChainParser


class TextReader:

    def read(self, path):

        try:

            df = pd.read_csv(path)

        except:

            df = pd.read_table(path)

        parser = OptionChainParser()

        return parser.parse(df)
        