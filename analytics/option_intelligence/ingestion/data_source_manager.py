from option_intelligence.ingestion.scraper.option_chain_scraper import OptionChainScraper


class DataSourceManager:

    def __init__(self):

        self.scraper = OptionChainScraper()

    def fetch(self):

        return self.scraper.fetch()