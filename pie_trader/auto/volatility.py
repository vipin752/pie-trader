"""ATR and volatility analytics, per timeframe.

No universal ATR: callers ask for the ATR of the timeframe they are working
on, and get percentile context so "is volatility expanding?" is a measured
answer, not an assumption.
"""
from __future__ import annotations

from bisect import bisect_left
from collections import deque
from dataclasses import dataclass, field

from .candles import Candle


@dataclass
class ATR:
    period: int = 14
    value: float = 0.0
    _prev_close: float | None = None
    _n: int = 0

    def update(self, c: Candle) -> float:
        tr = c.high - c.low
        if self._prev_close is not None:
            tr = max(tr, abs(c.high - self._prev_close), abs(c.low - self._prev_close))
        self._prev_close = c.close
        self._n += 1
        if self._n == 1:
            self.value = tr
        else:
            k = min(self._n, self.period)
            self.value += (tr - self.value) / k  # Wilder smoothing
        return self.value

    @property
    def ready(self) -> bool:
        return self._n >= self.period


@dataclass
class VolatilityEngine:
    """One ATR per timeframe plus a rolling percentile of ATR values,
    so regimes can be read as compression (<30th pct) / normal /
    expansion (>70th pct) of that timeframe's own recent history."""

    period: int = 14
    window: int = 400           # how many past ATR readings the percentile uses
    atrs: dict = field(default_factory=dict)       # tf minutes -> ATR
    history: dict = field(default_factory=dict)    # tf minutes -> deque

    def update(self, tf: int, c: Candle) -> float:
        a = self.atrs.setdefault(tf, ATR(self.period))
        v = a.update(c)
        h = self.history.setdefault(tf, deque(maxlen=self.window))
        if a.ready:
            h.append(v)
        return v

    def atr(self, tf: int) -> float:
        return self.atrs[tf].value if tf in self.atrs else 0.0

    def percentile(self, tf: int) -> float | None:
        """Where the current ATR sits in its own recent history, 0..1."""
        h = self.history.get(tf)
        if not h or len(h) < 30:
            return None
        s = sorted(h)
        return bisect_left(s, self.atr(tf)) / len(s)

    def state(self, tf: int) -> str:
        p = self.percentile(tf)
        if p is None:
            return "insufficient_data"
        return "compression" if p < 0.30 else "expansion" if p > 0.70 else "normal"
