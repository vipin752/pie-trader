class AngelSubscriber:

    def __init__(self, token_mapper, atm_calculator):
        self.token_mapper = token_mapper
        self.atm_calculator = atm_calculator

    def get_subscription_tokens(self, symbol, spot):

        atm = self.atm_calculator.get_atm(symbol)
        strikes = self.atm_calculator.get_strikes(symbol, atm)

        tokens = []

        for token, info in self.token_mapper.token_map.items():
            if info["symbol"] != symbol:
                continue

            if info["strike"] in strikes:
                tokens.append(token)

        return tokens
    