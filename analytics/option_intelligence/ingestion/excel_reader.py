import pandas as pd


class ExcelReader:

    def read(self, path):

        df = pd.read_excel(path)

        return df.to_dict()
        