"""Chart B — selected option-premium validation (spec §3).

Chart A (the underlying's structure/SMC read) finds the opportunity. This
module independently checks whether the SPECIFIC CONTRACT the engine picked
is actually tradable: not near-worthless, not carrying a spread that eats
the edge, not too small a notional. The engine requires BOTH checks to pass
— a directional signal never authorises a trade by itself (spec §3, §8).

`estimate_spread_pct` is a modelled stand-in (wider spreads on cheap
premium). A live adapter must replace it with the broker's real bid/ask —
this is clearly a model, not a quote, and the engine logs it as such.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .options import OptionContract


@dataclass
class PremiumCheck:
    ok: bool
    reasons: list = field(default_factory=list)
    spread_pct: float = 0.0

    def __bool__(self):
        return self.ok


def estimate_spread_pct(premium: float) -> float:
    if premium <= 0:
        return 1.0
    if premium < 5:
        return 0.15
    if premium < 20:
        return 0.06
    if premium < 60:
        return 0.03
    return 0.015


def validate_premium(con: OptionContract, premium: float, qty: int,
                     min_premium: float = 5.0, max_spread_pct: float = 0.08,
                     min_notional: float = 0.0) -> PremiumCheck:
    reasons = []
    if premium < min_premium:
        reasons.append(f"premium ₹{premium:.2f} is below the ₹{min_premium:g} floor — too close to worthless")
    spread = estimate_spread_pct(premium)
    if spread > max_spread_pct:
        reasons.append(f"modelled spread {spread:.0%} of premium exceeds the {max_spread_pct:.0%} limit (real quote not yet wired in)")
    notional = premium * qty
    if min_notional and notional < min_notional:
        reasons.append(f"notional ₹{notional:,.0f} is below the ₹{min_notional:,.0f} floor")
    return PremiumCheck(not reasons, reasons, spread)
