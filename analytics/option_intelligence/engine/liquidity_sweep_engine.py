from __future__ import annotations


class LiquiditySweepEngine:

    def __init__(self):
        pass

    def detect(self, spot, ladder, gamma):

        call_clusters = ladder.get("call_clusters", [])
        put_clusters = ladder.get("put_clusters", [])

        call_wall = gamma.get("call_gamma_wall", 0)
        put_wall = gamma.get("put_gamma_wall", 0)

        sweep_signal = None

        if call_clusters and spot > call_wall:
            sweep_signal = "CALL_SIDE_LIQUIDITY_SWEEP"

        elif put_clusters and spot < put_wall:
            sweep_signal = "PUT_SIDE_LIQUIDITY_SWEEP"

        else:
            sweep_signal = "NO_SWEEP"

        return {
            "signal": sweep_signal,
            "call_wall": call_wall,
            "put_wall": put_wall
        }
        