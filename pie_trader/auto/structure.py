"""Market structure: swings, break of structure (BOS), change of character
(CHoCH), liquidity pools and sweeps. Everything is confirmed on candle close
and uses only past data (no look-ahead): a swing at bar i is known at i+right.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .candles import Candle


@dataclass
class Swing:
    idx: int
    price: float
    kind: str  # "H" | "L"
    taken: bool = False  # liquidity beyond it has been swept/broken


@dataclass
class Event:
    idx: int
    kind: str        # BOS | CHOCH | SWEEP
    direction: str   # "up" | "down"
    level: float
    ref: Optional[Swing] = None


@dataclass
class Structure:
    left: int = 3
    right: int = 3
    eq_tolerance: float = 0.0004  # equal highs/lows within 0.04 %
    max_history: int = 500        # cap on highs/lows/events (space-complexity
                                   # audit, Part K2: these three grew without
                                   # bound over a long-running session before
                                   # this was added; nothing elsewhere indexes
                                   # into them by absolute position — only by
                                   # .idx attribute, negative slices or
                                   # reversed() — so trimming the front is safe)
    candles: list = field(default_factory=list)
    highs: list = field(default_factory=list)
    lows: list = field(default_factory=list)
    trend: str = "none"           # up | down | none
    events: list = field(default_factory=list)
    day_levels: dict = field(default_factory=dict)  # PDH/PDL/session extremes

    def update(self, c: Candle) -> list[Event]:
        self.candles.append(c)
        i = len(self.candles) - 1
        self._track_days(c)
        self._confirm_swing(i)
        new: list[Event] = []
        new += self._breaks(i, c)
        new += self._sweeps(i, c)
        self.events += new
        if len(self.highs) > self.max_history:
            del self.highs[: len(self.highs) - self.max_history]
        if len(self.lows) > self.max_history:
            del self.lows[: len(self.lows) - self.max_history]
        if len(self.events) > self.max_history:
            del self.events[: len(self.events) - self.max_history]
        return new

    # ── swings ──
    def _confirm_swing(self, i):
        p = i - self.right
        if p < self.left:
            return
        win = self.candles[p - self.left: i + 1]
        cp = self.candles[p]
        if cp.high == max(x.high for x in win) and all(x.high < cp.high for x in win[self.left + 1:]):
            self.highs.append(Swing(p, cp.high, "H"))
        if cp.low == min(x.low for x in win) and all(x.low > cp.low for x in win[self.left + 1:]):
            self.lows.append(Swing(p, cp.low, "L"))

    def last_high(self, untaken=True) -> Optional[Swing]:
        for s in reversed(self.highs):
            if not untaken or not s.taken:
                return s
        return None

    def last_low(self, untaken=True) -> Optional[Swing]:
        for s in reversed(self.lows):
            if not untaken or not s.taken:
                return s
        return None

    # ── BOS / CHoCH: a candle CLOSES beyond the latest unbroken swing ──
    def _breaks(self, i, c) -> list[Event]:
        out = []
        h, l = self.last_high(), self.last_low()
        if h and h.idx < i and c.close > h.price:
            kind = "BOS" if self.trend == "up" else "CHOCH"
            h.taken = True
            self.trend = "up"
            out.append(Event(i, kind, "up", h.price, h))
        if l and l.idx < i and c.close < l.price:
            kind = "BOS" if self.trend == "down" else "CHOCH"
            l.taken = True
            self.trend = "down"
            out.append(Event(i, kind, "down", l.price, l))
        return out

    # ── liquidity sweep: wick through a pool, close back inside ──
    def pools(self, side: str) -> list[float]:
        """Resting liquidity: untaken swing highs (buy-side) or lows (sell-side),
        equal highs/lows clusters, and previous-day high/low."""
        sw = self.highs if side == "buy" else self.lows
        lv = [s.price for s in sw if not s.taken][-6:]
        key = "pdh" if side == "buy" else "pdl"
        if self.day_levels.get(key):
            lv.append(self.day_levels[key])
        return lv

    def _sweeps(self, i, c) -> list[Event]:
        out = []
        for s in self.highs[-8:]:
            if not s.taken and s.idx < i and c.high > s.price and c.close < s.price:
                s.taken = True
                out.append(Event(i, "SWEEP", "down", s.price, s))  # buy-side taken → bearish
        for s in self.lows[-8:]:
            if not s.taken and s.idx < i and c.low < s.price and c.close > s.price:
                s.taken = True
                out.append(Event(i, "SWEEP", "up", s.price, s))    # sell-side taken → bullish
        for key, d in (("pdh", "down"), ("pdl", "up")):
            lvl = self.day_levels.get(key)
            if not lvl or self.day_levels.get(key + "_swept"):
                continue
            if (d == "down" and c.high > lvl and c.close < lvl) or (d == "up" and c.low < lvl and c.close > lvl):
                self.day_levels[key + "_swept"] = True
                out.append(Event(i, "SWEEP", d, lvl))
        return out

    def _track_days(self, c):
        d = self.day_levels
        if d.get("day") != c.ts.date():
            if d.get("day"):
                d["pdh"], d["pdl"] = d["hi"], d["lo"]
                d["pdh_swept"] = d["pdl_swept"] = False
            d["day"], d["hi"], d["lo"] = c.ts.date(), c.high, c.low
        d["hi"], d["lo"] = max(d["hi"], c.high), min(d["lo"], c.low)

    def next_target(self, direction: str, price: float) -> Optional[float]:
        """Nearest opposing liquidity beyond price: where a ride should aim."""
        if direction == "up":
            above = [p for p in self.pools("buy") if p > price]
            return min(above) if above else None
        below = [p for p in self.pools("sell") if p < price]
        return max(below) if below else None
