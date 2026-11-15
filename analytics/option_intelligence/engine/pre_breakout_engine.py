class PreBreakoutEngine:

    def detect(self, ctx):

        compression = ctx.get("market_structure", {}).get("compression", {})
        probability = ctx.get("market_structure", {}).get("probability_model", {})

        if compression.get("compression_detected"):

            return {
                "status": "READY",
                "message": "Compression detected → breakout soon",
                "direction": probability.get("direction")
            }

        return {
            "status": "NOT_READY"
        }
        