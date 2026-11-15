import pandas as pd


class CSVReader:

    def read(self, path):

        df = pd.read_csv(path)

        return df.to_dict()