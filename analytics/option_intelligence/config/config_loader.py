import configparser
import os


class ConfigLoader:

    def __init__(self):

        config = configparser.ConfigParser()

        config_path = os.path.join(
            os.path.dirname(__file__),
            "config.properties"
        )

        config.read(config_path)

        self.config = config

    def get(self, section, key):

        return self.config.get(section, key)
        