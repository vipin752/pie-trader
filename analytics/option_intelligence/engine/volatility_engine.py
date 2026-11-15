class VolatilityEngine:

    def expected_move(self, spot, strikes, expiry=None):

        rows = sorted(strikes, key=lambda x: abs(x["strike"] - spot))

        row = rows[0]

        call_iv = row.get("call_iv", 0)
        put_iv = row.get("put_iv", 0)

        iv = (call_iv + put_iv) / 2 if call_iv and put_iv else 15

        iv_fraction = iv / 100

        daily_move = spot * iv_fraction / (252 ** 0.5)

        weekly_move = spot * iv_fraction / (52 ** 0.5)

        return {

            "atm_iv_pct": round(iv, 2),

            "daily_move": round(daily_move, 2),
            "weekly_move": round(weekly_move, 2),

            "upper_1d": round(spot + daily_move, 2),
            "lower_1d": round(spot - daily_move, 2),

            "upper_1w": round(spot + weekly_move, 2),
            "lower_1w": round(spot - weekly_move, 2)
        }
        