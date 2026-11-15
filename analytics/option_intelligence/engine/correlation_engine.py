class CorrelationEngine:

    def analyze(self, index_move, sector_moves):

        bullish = sum(1 for x in sector_moves if x > 0)
        bearish = sum(1 for x in sector_moves if x < 0)

        if bullish > bearish:
            return "BULLISH_CONFIRMATION"

        if bearish > bullish:
            return "BEARISH_CONFIRMATION"

        return "NEUTRAL"
        