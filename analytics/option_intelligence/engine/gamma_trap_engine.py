from __future__ import annotations


class GammaTrapEngine:

    """
    Detects gamma traps and dealer squeeze zones.
    """

    def detect(self, spot, gamma, gex, liquidity):

        gamma_flip = gamma.get("gamma_flip", spot)

        call_wall = gamma.get("call_gamma_wall")

        put_wall = gamma.get("put_gamma_wall")

        regime = gex.get("gamma_regime")

        support = liquidity.get("support")

        resistance = liquidity.get("resistance")

        trap = "NONE"

        direction = "NEUTRAL"

        trigger_level = None

        # -----------------------------------
        # Upside trap
        # -----------------------------------

        if regime == "negative_gamma" and resistance:

            if spot > resistance:

                trap = "UPSIDE_GAMMA_SQUEEZE"

                direction = "STRONG_UP"

                trigger_level = resistance

        # -----------------------------------
        # Downside trap
        # -----------------------------------

        if regime == "negative_gamma" and support:

            if spot < support:

                trap = "DOWNSIDE_GAMMA_CRASH"

                direction = "STRONG_DOWN"

                trigger_level = support

        # -----------------------------------
        # Pre-breakout pressure
        # -----------------------------------

        if trap == "NONE":

            if resistance and abs(resistance - spot) < 50:

                trap = "UPSIDE_TRAP_FORMING"

                direction = "POTENTIAL_UP"

                trigger_level = resistance

            elif support and abs(spot - support) < 50:

                trap = "DOWNSIDE_TRAP_FORMING"

                direction = "POTENTIAL_DOWN"

                trigger_level = support

        return {

            "gamma_trap_signal": trap,

            "direction": direction,

            "trigger_level": trigger_level,

            "gamma_flip": gamma_flip
        }
        