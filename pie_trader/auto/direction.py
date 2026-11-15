"""Market Direction Engine (spec §2A).

Reads 15m, 30m, 60m, Daily, Weekly and Monthly structure independently and
combines them into one directional read. Higher timeframes (D/W/M) carry the
vote that decides STRONG vs WEAK; intraday timeframes (15/30/60) contribute
to the majority but don't by themselves make a read "strong".

This does not predict direction — it reports what multiple timeframes'
own swing structure currently shows, with each timeframe's evidence kept
separate so the read is auditable (spec §5/§14.10).
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .candles import Candle, PeriodAggregator, daily_key, minute_key, monthly_key, weekly_key
from .regime import Regime, _swing_bias
from .structure import Structure
from .volatility import VolatilityEngine

INTRADAY_TFS = (15, 30, 60)
HTF_LABELS = ("D", "W", "M")
MIN_BARS = 6  # fewer bars than this on a timeframe → insufficient data for it


def _key_fn(tf):
    return {"D": daily_key, "W": weekly_key, "M": monthly_key}.get(tf) or minute_key(tf)


@dataclass
class MarketDirectionEngine:
    timeframes: tuple = INTRADAY_TFS + HTF_LABELS
    vol: VolatilityEngine = field(default_factory=VolatilityEngine)
    structures: dict = field(default_factory=dict)
    _agg: dict = field(default_factory=dict)

    def __post_init__(self):
        for tf in self.timeframes:
            self.structures.setdefault(tf, Structure(2, 2))
            self._agg.setdefault(tf, PeriodAggregator(_key_fn(tf)))

    def update(self, c: Candle) -> None:
        for tf, agg in self._agg.items():
            done = agg.update(c)
            if done is not None:
                self.structures[tf].update(done)
                if isinstance(tf, int):
                    self.vol.update(tf, done)

    def read(self) -> dict:
        """One Regime per timeframe, independently classified."""
        out = {}
        for tf, st in self.structures.items():
            if len(st.candles) < MIN_BARS:
                out[tf] = Regime("INSUFFICIENT_DATA", "none", "insufficient_data",
                                 [f"{tf}: only {len(st.candles)} closed bar(s)"])
                continue
            bias, trend = _swing_bias(st), st.trend
            vs = self.vol.state(tf) if isinstance(tf, int) else "n/a"
            if trend != "none" and bias != "none" and trend != bias:
                out[tf] = Regime("CONFLICTING", "none", vs,
                                 [f"{tf}: BOS/CHoCH trend={trend} disagrees with swing sequence={bias}"])
                continue
            d = trend if trend != "none" else bias
            name = ("STRONG_" if bias == d and d != "none" else "WEAK_") + ("BULL" if d == "up" else "BEAR") \
                if d != "none" else "SIDEWAYS"
            out[tf] = Regime(name, d, vs, [f"{tf}: trend={trend} bias={bias} vol={vs}"])
        return out

    def overall(self) -> Regime:
        """Combine every timeframe's read. D/W/M agreement decides STRONG vs
        WEAK; intraday timeframes vote but never outvote a clean D/W/M read."""
        reads = self.read()
        evidence = [f"{tf}={r.direction or 'none'}({r.name})" for tf, r in reads.items()]
        dirs = [r.direction for r in reads.values() if r.direction in ("up", "down")]
        if not dirs:
            return Regime("INSUFFICIENT_DATA", "none", "n/a", evidence)
        up, down = dirs.count("up"), dirs.count("down")
        if up and down:
            return Regime("CONFLICTING", "none", "n/a", evidence)
        d = "up" if up else "down"
        htf_agree = sum(1 for tf in HTF_LABELS if reads[tf].direction == d)
        strong = htf_agree >= 2
        name = ("STRONG_" if strong else "WEAK_") + ("BULL" if d == "up" else "BEAR")
        return Regime(name, d, "n/a", evidence)
