from __future__ import annotations

import csv
import datetime as dt
from dataclasses import dataclass


@dataclass
class Candle:
    ts: dt.datetime
    open: float
    high: float
    low: float
    close: float
    volume: float = 0.0


def load_candles(path) -> list[Candle]:
    """CSV with columns: datetime (ISO, IST), open, high, low, close[, volume]."""
    out = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            k = {c.lower().strip(): v for c, v in r.items()}
            ts = k.get("datetime") or k.get("timestamp") or k.get("date")
            out.append(Candle(dt.datetime.fromisoformat(ts.replace("Z", "")).replace(tzinfo=None),
                              float(k["open"]), float(k["high"]), float(k["low"]), float(k["close"]),
                              float(k.get("volume") or 0)))
    out.sort(key=lambda c: c.ts)
    return out


def resample(candles: list[Candle], minutes: int) -> list[Candle]:
    out: list[Candle] = []
    for c in candles:
        m = (c.ts.hour * 60 + c.ts.minute - 555) // minutes * minutes + 555  # anchor 09:15
        key = c.ts.replace(hour=m // 60, minute=m % 60, second=0, microsecond=0)
        if out and out[-1].ts == key:
            b = out[-1]
            b.high, b.low, b.close, b.volume = max(b.high, c.high), min(b.low, c.low), c.close, b.volume + c.volume
        else:
            out.append(Candle(key, c.open, c.high, c.low, c.close, c.volume))
    return out


@dataclass
class PeriodAggregator:
    """Rolls candles into whatever period `key_fn` defines (an intraday
    minute bucket, a calendar day, an ISO week, a calendar month, ...).
    `update` returns the just-completed bar when the period rolls over,
    else None — so callers can feed a Structure/ATR only on a closed bar."""
    key_fn: "callable"
    _key: object = None
    _bar: "Candle | None" = None

    def update(self, c: Candle):
        k = self.key_fn(c)
        if self._bar is None or k != self._key:
            done = self._bar
            self._key, self._bar = k, Candle(c.ts, c.open, c.high, c.low, c.close, c.volume)
            return done
        b = self._bar
        b.high, b.low, b.close = max(b.high, c.high), min(b.low, c.low), c.close
        b.volume += c.volume
        return None

    def flush(self):
        """Return the in-progress bar (e.g. at end of data), or None."""
        return self._bar


def minute_key(minutes: int):
    def key(c: Candle):
        m = (c.ts.hour * 60 + c.ts.minute - 555) // minutes * minutes + 555
        return c.ts.date(), m
    return key


def daily_key(c: Candle):
    return c.ts.date()


def weekly_key(c: Candle):
    return c.ts.date().isocalendar()[:2]  # (ISO year, ISO week)


def monthly_key(c: Candle):
    return c.ts.year, c.ts.month
