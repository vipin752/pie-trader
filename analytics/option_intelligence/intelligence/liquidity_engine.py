class LiquidityEngine:

    def detect(self, spot, ladder):

        resistance = ladder["call_resistance_zone"][0]
        support = ladder["put_support_zone"][0]

        if spot > resistance:
            return "UPSIDE_LIQUIDITY_SWEEP"

        if spot < support:
            return "DOWNSIDE_LIQUIDITY_SWEEP"

        return "RANGE"
        