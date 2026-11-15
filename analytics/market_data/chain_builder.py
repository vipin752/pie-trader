class ChainBuilder:

    def __init__(self, token_mapper):
        self.token_mapper = token_mapper
        self.option_chain = {}

    def on_tick(self, tick: dict):
        token = str(tick.get("token", ""))
        info  = self.token_mapper.get_info(token)
        if not info:
            return

        strike   = info["strike"]
        opt_type = info["type"]

        if strike not in self.option_chain:
            self.option_chain[strike] = {"CE": {}, "PE": {}}

        self.option_chain[strike][opt_type] = {
            "ltp":    tick.get("ltp", 0.0),
            "oi":     tick.get("oi", 0),
            "volume": tick.get("volume", 0),
            "bid":    tick.get("bid", 0.0),
            "ask":    tick.get("ask", 0.0),
            "iv":     tick.get("iv", 0.0),
        }

    def get_chain(self) -> dict:
        return self.option_chain
