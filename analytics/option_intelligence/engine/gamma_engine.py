from __future__ import annotations
import math


class GammaEngine:

    def __init__(self):
        pass


    # ------------------------------------------------
    # Normal distribution PDF
    # ------------------------------------------------

    def _norm_pdf(self, x):
        return math.exp(-0.5 * x * x) / math.sqrt(2 * math.pi)


    # ------------------------------------------------
    # Black-Scholes Gamma
    # ------------------------------------------------

    def _bs_gamma(self, S, K, T, sigma):

        if sigma <= 0 or T <= 0:
            return 0.0

        d1 = (math.log(S / K) + 0.5 * sigma ** 2 * T) / (sigma * math.sqrt(T))

        gamma = self._norm_pdf(d1) / (S * sigma * math.sqrt(T))

        return gamma


    # ------------------------------------------------
    # Main Exposure Calculation
    # ------------------------------------------------

    def calculate_exposure(self, strikes, spot):

        enriched = []

        call_gamma_wall = None
        put_gamma_wall = None

        max_call_gamma = -1
        max_put_gamma = -1

        gamma_sum = 0

        # assumptions (can later come from IV surface)
        sigma = 0.20
        T = 1 / 365

        for row in strikes:

            strike = row["strike"]

            call_oi = row.get("call_oi", 0)
            put_oi = row.get("put_oi", 0)

            call_gamma = self._bs_gamma(spot, strike, T, sigma)
            put_gamma = self._bs_gamma(spot, strike, T, sigma)

            row["call_gamma"] = call_gamma
            row["put_gamma"] = put_gamma

            enriched.append(row)

            call_gex = call_gamma * call_oi
            put_gex = put_gamma * put_oi

            gamma_sum += call_gex - put_gex

            if call_gex > max_call_gamma:
                max_call_gamma = call_gex
                call_gamma_wall = strike

            if put_gex > max_put_gamma:
                max_put_gamma = put_gex
                put_gamma_wall = strike


        gamma_flip = None

        if call_gamma_wall and put_gamma_wall:
            gamma_flip = (call_gamma_wall + put_gamma_wall) / 2


        return {

            "gamma_flip": gamma_flip,
            "call_gamma_wall": call_gamma_wall,
            "put_gamma_wall": put_gamma_wall,
            "net_gamma": gamma_sum,
            "strikes_enriched": enriched
        }
        