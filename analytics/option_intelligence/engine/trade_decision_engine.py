from __future__ import annotations


class TradeDecisionEngine:

    def generate(
        self,
        spot,
        gamma,
        gex,
        compression,
        liquidity,
        zero_gamma
    ):

        call_wall = gamma["call_gamma_wall"]
        put_wall = gamma["put_gamma_wall"]

        gamma_flip = gamma["gamma_flip"]

        regime = gex["gamma_regime"]

        compressed = compression["compression_detected"]

        support = liquidity["support"]
        resistance = liquidity["resistance"]

        zero = zero_gamma["zero_gamma"]

        # --------------------------
        # Market State
        # --------------------------

        if compressed:
            state = "GAMMA_COMPRESSION"

        elif regime == "positive_gamma":
            state = "GAMMA_PIN"

        else:
            state = "VOLATILITY_EXPANSION"

        # --------------------------
        # Bias
        # --------------------------

        if spot > gamma_flip:
            bias = "UPSIDE"

        elif spot < gamma_flip:
            bias = "DOWNSIDE"

        else:
            bias = "NEUTRAL"

        # --------------------------
        # Strategy
        # --------------------------

        if state == "GAMMA_COMPRESSION":

            strategy = "RANGE_SELL"

        elif regime == "positive_gamma":

            strategy = "MEAN_REVERSION"

        else:

            strategy = "BREAKOUT_BUY"

        # --------------------------
        # Range
        # --------------------------

        range_low = put_wall
        range_high = call_wall

        # --------------------------
        # Breakouts
        # --------------------------

        breakout_long = call_wall
        breakout_short = put_wall

        # --------------------------
        # Targets
        # --------------------------

        targets_up = [
            call_wall + 100,
            call_wall + 300
        ]

        targets_down = [
            put_wall - 100,
            put_wall - 300
        ]

        return {

            "market_state": state,

            "bias": bias,

            "range_low": range_low,
            "range_high": range_high,

            "strategy": strategy,

            "breakout_long": breakout_long,
            "breakout_short": breakout_short,

            "targets_up": targets_up,
            "targets_down": targets_down,

            "zero_gamma": zero
        }
        