from __future__ import annotations


class DealerInventoryEngine:

    """
    Dealer inventory imbalance detection.

    Determines whether dealers are:
    - Long gamma
    - Short gamma
    - Forced hedging

    Used to detect:
    - Gamma squeezes
    - Volatility expansion
    """

    def calculate(self, spot, gamma, gex, pressure):

        net_gamma = gamma.get("net_gamma", 0)

        gamma_flip = gamma.get("gamma_flip", spot)

        regime = gex.get("gamma_regime")

        pressure_value = pressure.get("pressure", 0)

        # -------------------------------------
        # Dealer inventory state
        # -------------------------------------

        if regime == "positive_gamma":
            inventory = "LONG_GAMMA"

        elif regime == "negative_gamma":
            inventory = "SHORT_GAMMA"

        else:
            inventory = "NEUTRAL"

        # -------------------------------------
        # Hedging pressure
        # -------------------------------------

        if spot > gamma_flip and regime == "negative_gamma":

            hedging = "BUY_FUTURES"

        elif spot < gamma_flip and regime == "negative_gamma":

            hedging = "SELL_FUTURES"

        else:

            hedging = "MEAN_REVERT"

        # -------------------------------------
        # Squeeze detection
        # -------------------------------------

        if regime == "negative_gamma" and abs(pressure_value) > 40:

            squeeze_risk = "HIGH"

        elif regime == "negative_gamma":

            squeeze_risk = "MEDIUM"

        else:

            squeeze_risk = "LOW"

        # -------------------------------------
        # Volatility regime
        # -------------------------------------

        if regime == "negative_gamma":

            volatility = "EXPANDING_VOL"

        else:

            volatility = "COMPRESSED_VOL"

        return {

            "dealer_inventory": inventory,

            "hedging_behavior": hedging,

            "gamma_squeeze_risk": squeeze_risk,

            "volatility_regime": volatility,

            "gamma_flip": gamma_flip,

            "net_gamma": net_gamma
        }
        