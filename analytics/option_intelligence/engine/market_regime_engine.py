from __future__ import annotations


class MarketRegimeEngine:

    """
    Determine market regime using gamma positioning,
    compression signals and expected volatility.
    """

    def detect(
        self,
        spot,
        gamma,
        gex,
        compression,
        volatility
    ):

        gamma_regime = gex.get("gamma_regime")

        compressed = compression.get("compression_detected", False)

        daily_move = volatility.get("daily_move", 0)

        call_wall = gamma.get("call_gamma_wall", 0)
        put_wall = gamma.get("put_gamma_wall", 0)

        width = abs(call_wall - put_wall)

        # -------- REGIME DETECTION --------

        if compressed:
            regime = "GAMMA_COMPRESSION"

        elif gamma_regime == "positive_gamma":
            regime = "RANGE_DAY"

        elif gamma_regime == "negative_gamma":
            regime = "TREND_DAY"

        else:
            regime = "NEUTRAL"

        # -------- VOLATILITY STATE --------

        if width < daily_move * 0.5:
            volatility_state = "LOW_VOL"

        else:
            volatility_state = "EXPANDING_VOL"

        return {

            "market_regime": regime,

            "volatility_state": volatility_state,

            "range_width": width,

            "expected_daily_move": daily_move
        }
        