class VolatilityEngine:

    def expected_move(self, spot):

        # placeholder
        move = spot * 0.01

        return {
            "expected_move": round(move, 2),
            "upper_range": round(spot + move, 2),
            "lower_range": round(spot - move, 2)
        }
        