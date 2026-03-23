from __future__ import annotations


class CompressionEngine:

    def __init__(self):
        pass


    def detect(self, spot, gamma, ladder):

        call_wall = gamma.get("call_gamma_wall", 0)
        put_wall = gamma.get("put_gamma_wall", 0)
        gamma_flip = gamma.get("gamma_flip", spot)

        if spot == 0:
            return {
                "compression_detected": False,
                "wall_spread_pct": 0,
                "flip_distance_pct": 0,
                "breakout_signal": "NO_COMPRESSION"
            }

        wall_pct = abs(call_wall - put_wall) / spot * 100 if call_wall and put_wall else 0
        flip_pct = abs(spot - gamma_flip) / spot * 100 if gamma_flip else 0

        compressed = wall_pct < 1.0 and flip_pct < 0.5

        if compressed:
            signal = "IMMINENT_BREAKOUT"
        elif wall_pct < 2:
            signal = "COMPRESSION_BUILDING"
        else:
            signal = "NO_COMPRESSION"

        return {
            "compression_detected": compressed,
            "wall_spread_pct": round(wall_pct, 3),
            "flip_distance_pct": round(flip_pct, 3),
            "breakout_signal": signal
        }
        