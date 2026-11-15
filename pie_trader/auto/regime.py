"""Market regime classification from price structure and volatility.

First version: price-based only. VIX, breadth, option-chain and futures
inputs are listed as `unavailable` until a data feed provides them — they are
never invented (spec §4, §14.4).
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .structure import Structure
from .volatility import VolatilityEngine

REGIMES = (
    "STRONG_BULL", "WEAK_BULL", "STRONG_BEAR", "WEAK_BEAR", "SIDEWAYS",
    "COMPRESSION", "EXPANSION", "CONFLICTING", "INSUFFICIENT_DATA",
)


@dataclass
class Regime:
    name: str
    direction: str              # up | down | none
    vol_state: str              # compression | normal | expansion | insufficient_data
    evidence: list = field(default_factory=list)
    unavailable: list = field(default_factory=lambda: ["vix", "pcr", "oi", "breadth", "futures_basis"])

    @property
    def tradeable(self) -> bool:
        return self.name not in ("CONFLICTING", "INSUFFICIENT_DATA")


def _swing_bias(s: Structure, n: int = 4) -> str:
    """Higher-highs/higher-lows vs lower-lows/lower-highs over the last swings."""
    hs = [x.price for x in s.highs[-n:]]
    ls = [x.price for x in s.lows[-n:]]
    # one-sided markets confirm swings on only one side; read that side alone
    if len(hs) < 2 and len(ls) >= 3:
        return "up" if all(b > a for a, b in zip(ls, ls[1:])) else "none"
    if len(ls) < 2 and len(hs) >= 3:
        return "down" if all(b < a for a, b in zip(hs, hs[1:])) else "none"
    if len(hs) < 2 or len(ls) < 2:
        return "none"
    hh = sum(b > a for a, b in zip(hs, hs[1:]))
    hl = sum(b > a for a, b in zip(ls, ls[1:]))
    ll = sum(b < a for a, b in zip(ls, ls[1:]))
    lh = sum(b < a for a, b in zip(hs, hs[1:]))
    if hh + hl > ll + lh and hh >= 1 and hl >= 1:
        return "up"
    if ll + lh > hh + hl and ll >= 1 and lh >= 1:
        return "down"
    return "none"


def classify(htf: Structure, exec_tf: Structure, vol: VolatilityEngine, exec_minutes: int) -> Regime:
    ev = []
    if len(htf.candles) < 20:
        return Regime("INSUFFICIENT_DATA", "none", "insufficient_data", ["fewer than 20 HTF candles"])
    bias = _swing_bias(htf)
    trend = htf.trend
    vs = vol.state(exec_minutes)
    ev.append(f"HTF trend={trend}, swing bias={bias}, vol={vs}")

    if trend != "none" and bias != "none" and trend != bias:
        return Regime("CONFLICTING", "none", vs, ev + ["HTF break direction disagrees with swing sequence"])
    d = trend if trend != "none" else bias
    if d == "none":
        name = "COMPRESSION" if vs == "compression" else "SIDEWAYS"
        return Regime(name, "none", vs, ev)
    strong = (bias == d) and vs != "compression"
    ltf_agrees = exec_tf.trend in (d, "none")
    if not ltf_agrees:
        ev.append("execution TF currently counter-trend (pullback or early reversal)")
    name = ("STRONG_" if strong else "WEAK_") + ("BULL" if d == "up" else "BEAR")
    if vs == "expansion":
        ev.append("volatility expansion: breakout family preferred")
    return Regime(name, d, vs, ev)
