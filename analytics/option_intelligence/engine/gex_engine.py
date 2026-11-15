from __future__ import annotations

from option_intelligence.config.contract_config import get_lot_size


class GEXEngine:

    def __init__(self):
        pass


    def calculate(self, strikes, spot, symbol: str = "NIFTY", atm_window=None):

        symbol = symbol.upper()

        lot_size = get_lot_size(symbol)

        scale = spot ** 2 * 0.01

        filtered = [
            s for s in strikes
            if atm_window is None or s["strike"] in atm_window
        ]

        total_gex = 0.0
        levels = []

        for row in filtered:

            call_gamma = row.get("call_gamma", 0.01)
            put_gamma = row.get("put_gamma", 0.01)

            call_oi = row.get("call_oi", 0)
            put_oi = row.get("put_oi", 0)

            strike = row["strike"]

            net = (call_gamma * call_oi - put_gamma * put_oi) * lot_size * scale

            total_gex += net

            levels.append({
                "strike": strike,
                "gex": round(net, 2)
            })

        levels.sort(key=lambda x: abs(x["gex"]), reverse=True)

        gamma_regime = "positive_gamma" if total_gex >= 0 else "negative_gamma"

        return {
            "symbol": symbol,
            "lot_size": lot_size,
            "total_gex": round(total_gex, 2),
            "gamma_regime": gamma_regime,
            "levels": levels[:10],
        }
        