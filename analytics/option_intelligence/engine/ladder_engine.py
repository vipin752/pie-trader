from __future__ import annotations


class LadderEngine:

    def __init__(self):
        pass

    def detect_clusters(self, strikes):

        if not strikes:
            return {
                "call_clusters": [],
                "put_clusters": []
            }

        call_levels = []
        put_levels = []

        for row in strikes:

            strike = row.get("strike")

            call_oi = row.get("call_oi", 0)
            put_oi = row.get("put_oi", 0)

            call_levels.append({
                "strike": strike,
                "oi": call_oi
            })

            put_levels.append({
                "strike": strike,
                "oi": put_oi
            })

        # sort by OI strength
        call_levels.sort(key=lambda x: x["oi"], reverse=True)
        put_levels.sort(key=lambda x: x["oi"], reverse=True)

        return {
            "call_clusters": call_levels[:10],
            "put_clusters": put_levels[:10]
        }
        