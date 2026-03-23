from __future__ import annotations

class DealerAccelerationEngine:

    """
    Detect dealer hedging acceleration zones.
    When price approaches large GEX levels near zero gamma,
    dealers hedge aggressively -> fast directional moves.
    """

    def detect(
        self,
        spot,
        gex,
        zero_gamma,
        liquidity
    ):

        gamma_regime = gex.get("gamma_regime")

        levels = gex.get("levels", [])

        flip = zero_gamma.get("zero_gamma")

        support = liquidity.get("support")
        resistance = liquidity.get("resistance")

        acceleration_up = None
        acceleration_down = None

        # find strongest gamma levels above and below spot

        for lvl in levels:

            strike = lvl["strike"]

            if strike > spot and not acceleration_up:
                acceleration_up = strike

            if strike < spot:
                acceleration_down = strike

        distance_to_flip = abs(spot - flip)

        # acceleration probability

        if gamma_regime == "negative_gamma":
            accel_state = "HIGH_ACCELERATION_RISK"

        elif distance_to_flip < 50:
            accel_state = "FLIP_ACCELERATION_ZONE"

        else:
            accel_state = "LOW_ACCELERATION"

        # directional trigger

        if spot > flip:
            directional_bias = "UPSIDE_ACCELERATION"
        else:
            directional_bias = "DOWNSIDE_ACCELERATION"

        return {

            "acceleration_state": accel_state,

            "directional_bias": directional_bias,

            "zero_gamma": flip,

            "distance_to_flip": round(distance_to_flip,2),

            "acceleration_up_trigger": acceleration_up,

            "acceleration_down_trigger": acceleration_down,

            "liquidity_support": support,

            "liquidity_resistance": resistance
        }
        