from typing import Dict, List, Any


class LiquidityEngine:

    # =========================================================
    # MAIN ENTRY (USED BY OPTION ENGINE)
    # =========================================================
    def zones(self, ladder: Dict[str, List[Dict]], spot: float) -> Dict[str, Any]:
        """
        Extract dynamic support & resistance using OI clusters.
        Institutional logic:
        - Resistance → highest CALL OI above spot
        - Support → highest PUT OI below spot
        """

        if not ladder:
            return {"support": None, "resistance": None}

        call_clusters = ladder.get("call_clusters", [])
        put_clusters = ladder.get("put_clusters", [])

        resistance = self._max_oi_strike(call_clusters, spot, side="CALL")
        support = self._max_oi_strike(put_clusters, spot, side="PUT")

        return {
            "support": support,
            "resistance": resistance
        }

    # =========================================================
    # CORE LOGIC (SPOT-AWARE OI SELECTION)
    # =========================================================
    def _max_oi_strike(
        self,
        clusters: List[Dict],
        spot: float,
        side: str
    ) -> Any:
        """
        Select strike based on:
        - CALL → above spot (resistance)
        - PUT → below spot (support)
        """

        if not clusters:
            return None

        # Filter based on side
        if side == "CALL":
            filtered = [c for c in clusters if c.get("strike") is not None and c["strike"] >= spot]
        else:
            filtered = [p for p in clusters if p.get("strike") is not None and p["strike"] <= spot]

        # fallback if no filtered results
        target = filtered if filtered else clusters

        # pick highest OI
        best = max(target, key=lambda x: x.get("oi", 0))

        return best.get("strike")

    # =========================================================
    # OPTIONAL (DEBUG / EXTENDED USE)
    # =========================================================
    def full_structure(self, ladder: Dict[str, List[Dict]], spot: float) -> Dict[str, Any]:
        """
        Extended view (if needed later for UI or ML)
        """

        zones = self.zones(ladder, spot)

        return {
            "support_resistance": zones,
            "call_clusters": ladder.get("call_clusters", []),
            "put_clusters": ladder.get("put_clusters", [])
        }
        