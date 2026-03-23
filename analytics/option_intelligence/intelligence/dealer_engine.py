class DealerEngine:

    def analyze(self, gamma):

        if gamma["gamma_flip"] > gamma["call_gamma_wall"]:
            state = "NEGATIVE_GAMMA"

        else:
            state = "POSITIVE_GAMMA"

        return {
            "dealer_position": state
        }
        