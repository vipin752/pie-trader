"""Smart Money Concepts (SMC/ICT) on top of the structure engine.

Implemented, all confirmed on closed candles with no look-ahead:
- Fair Value Gaps (FVG/imbalance): 3-candle gap; tracked until filled.
- Order Blocks: the last opposite-close candle before a BOS/CHoCH; tracked
  until mitigated (price trades back into it) or invalidated (close through).
- Dealing range premium/discount: equilibrium of the last significant swing
  pair; longs belong in discount, shorts in premium.
- OTE (optimal trade entry): the 62–79% retracement pocket of the last impulse.

Everything here is evidence for a setup, not a signal by itself.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .candles import Candle
from .structure import Structure


@dataclass
class Zone:
    kind: str          # "FVG" | "OB"
    direction: str     # "up" (bullish demand) | "down" (bearish supply)
    top: float
    bottom: float
    created_idx: int
    mitigated: bool = False     # price has traded back into the zone
    invalidated: bool = False   # candle CLOSED through the far side

    def contains(self, price: float) -> bool:
        return self.bottom <= price <= self.top

    @property
    def mid(self) -> float:
        return (self.top + self.bottom) / 2


@dataclass
class SMC:
    structure: Structure
    max_zones: int = 12
    fvgs: list = field(default_factory=list)
    obs: list = field(default_factory=list)
    _i: int = -1

    # ── per-candle update; call AFTER structure.update(c) ──
    def update(self, c: Candle, events: list) -> None:
        self._i += 1
        cs = self.structure.candles
        i = self._i
        # FVG: bullish when candle[i-2].high < candle[i].low (gap left by i-1's impulse)
        if i >= 2:
            a, b = cs[i - 2], c
            if a.high < b.low:
                self._add(self.fvgs, Zone("FVG", "up", b.low, a.high, i))
            if a.low > b.high:
                self._add(self.fvgs, Zone("FVG", "down", a.low, b.high, i))
        # Order block: on a break event, the last opposite-close candle before the break
        for ev in events:
            if ev.kind not in ("BOS", "CHOCH"):
                continue
            rng = range(i - 1, max(i - 15, 0), -1)
            if ev.direction == "up":
                j = next((k for k in rng if cs[k].close < cs[k].open), None)
                if j is not None:
                    self._add(self.obs, Zone("OB", "up", cs[j].high, cs[j].low, j))
            else:
                j = next((k for k in rng if cs[k].close > cs[k].open), None)
                if j is not None:
                    self._add(self.obs, Zone("OB", "down", cs[j].high, cs[j].low, j))
        # zone lifecycle
        for z in self.fvgs + self.obs:
            if z.invalidated or z.created_idx == i:
                continue
            if z.direction == "up":
                if c.close < z.bottom:
                    z.invalidated = True
                elif c.low <= z.top:
                    z.mitigated = True
            else:
                if c.close > z.top:
                    z.invalidated = True
                elif c.high >= z.bottom:
                    z.mitigated = True

    def _add(self, lst, z: Zone):
        lst.append(z)
        if len(lst) > self.max_zones:
            lst.pop(0)

    # ── queries ──
    def fresh_zone_at(self, price: float, direction: str) -> Optional[Zone]:
        """An unmitigated, valid OB or FVG of the given direction containing price
        (order blocks preferred over FVGs)."""
        for lst in (self.obs, self.fvgs):
            for z in reversed(lst):
                if z.direction == direction and not z.mitigated and not z.invalidated and z.contains(price):
                    return z
        return None

    def dealing_range(self):
        h = self.structure.last_high(untaken=False)
        l = self.structure.last_low(untaken=False)
        if not h or not l:
            return None
        return (l.price, h.price)

    def in_discount(self, price: float) -> Optional[bool]:
        """True = lower half of the dealing range (where longs belong)."""
        r = self.dealing_range()
        if not r or r[1] <= r[0]:
            return None
        return price <= (r[0] + r[1]) / 2

    def in_ote(self, price: float, direction: str) -> Optional[bool]:
        """Inside the 62–79% retracement pocket of the current dealing range."""
        r = self.dealing_range()
        if not r or r[1] <= r[0]:
            return None
        lo, hi = r
        span = hi - lo
        if direction == "up":     # long: retracement measured down from the high
            return hi - 0.79 * span <= price <= hi - 0.62 * span
        return lo + 0.62 * span <= price <= lo + 0.79 * span
