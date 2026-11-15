from __future__ import annotations


class VolumeSpikeEngine:

    def __init__(self):
        pass

    def detect(self, strikes):

        if not strikes:
            return {"spikes": []}

        volumes = []

        for row in strikes:

            call_vol = row.get("call_volume", 0)
            put_vol = row.get("put_volume", 0)

            volumes.append(call_vol + put_vol)

        avg_vol = sum(volumes) / len(volumes)

        spikes = []

        for row in strikes:

            strike = row["strike"]

            call_vol = row.get("call_volume", 0)
            put_vol = row.get("put_volume", 0)

            total_vol = call_vol + put_vol

            if total_vol > avg_vol * 3:

                spikes.append({
                    "strike": strike,
                    "volume": total_vol,
                    "type": "UNUSUAL_ACTIVITY"
                })

        return {
            "spikes": spikes[:10]
        }
        