class StrikeSelector:

    def select_best_strike(self, option_chain):
        best_token = None
        best_score = 0

        for token, data in option_chain.items():
            volume = data.get("volume", 0)
            oi = data.get("oi", 0)
            spread = abs((data.get("ask", 0) or 0) - (data.get("bid", 0) or 0))

            score = volume + oi - spread * 100

            if score > best_score:
                best_score = score
                best_token = token

        return best_token
    