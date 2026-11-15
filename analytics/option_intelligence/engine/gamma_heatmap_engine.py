from __future__ import annotations


class GammaHeatmapEngine:

    """
    Builds gamma heatmap across strikes.
    Used to visualize where dealer gamma concentration exists.
    """

    def build(self, strikes):

        heatmap = []

        for row in strikes:

            strike = row["strike"]

            call_gamma = row.get("call_gamma", 0)
            put_gamma = row.get("put_gamma", 0)

            call_oi = row.get("call_oi", 0)
            put_oi = row.get("put_oi", 0)

            call_exposure = call_gamma * call_oi
            put_exposure = put_gamma * put_oi

            net_gamma = call_exposure - put_exposure

            heatmap.append({

                "strike": strike,

                "call_gamma_exposure": round(call_exposure, 4),

                "put_gamma_exposure": round(put_exposure, 4),

                "net_gamma": round(net_gamma, 4)

            })

        heatmap.sort(key=lambda x: x["strike"])

        return {
            "gamma_heatmap": heatmap
        }
        