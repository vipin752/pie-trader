from __future__ import annotations


class StrikeGravityEngine:

    """
    Detects strikes where price tends to gravitate.

    Used to detect:
    - Pinning
    - Magnet levels
    - Breakout zones
    """

    def detect(self, spot, heatmap):

        strongest = sorted(
            heatmap,
            key=lambda x: abs(x["net_gamma"]),
            reverse=True
        )[:5]

        gravity_levels = [x["strike"] for x in strongest]

        nearest = min(gravity_levels, key=lambda x: abs(x - spot))

        if spot > nearest:

            direction = "DOWNWARD_GRAVITY"

        elif spot < nearest:

            direction = "UPWARD_GRAVITY"

        else:

            direction = "PINNED"

        return {

            "gravity_strikes": gravity_levels,

            "primary_gravity": nearest,

            "gravity_direction": direction
        }
        