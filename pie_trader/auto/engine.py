"""The automated session-based option engine."""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field, fields
from pathlib import Path
from typing import Callable, Optional

from ..rules import Guard, RuleSet
from .broker import Broker
from .candles import Candle
from .options import OptionContract, Underlying, choose_contract, size_lots
from .structure import Event, Structure
from .volatility import VolatilityEngine


def _t(s: str) -> dt.time:
    h, m = map(int, s.split(":"))
    return dt.time(h, m)


@dataclass
class AutoConfig:
    underlying: str = "NIFTY"
    timeframe: int = 5                      # minutes, execution chart
    htf: int = 15                           # minutes, bias chart
    windows: list = field(default_factory=lambda: [["09:20", "10:45"], ["11:30", "12:45"], ["13:45", "14:45"]])
    max_trades_per_window: int = 0          # 0 = no per-session cap
    eod_exit: str = "15:15"
    setups: list = field(default_factory=lambda: ["sweep_bos", "trend_bos", "pdhl_break"])
    pdhl_body_frac: float = 0.8             # breakout candle must close with ≥80% body beyond PDH/PDL
    sma_filter: bool = False                # longs only above / shorts only below the 26-SMA
    sma_period: int = 26
    stop_and_reverse: bool = True           # failed PDH/PDL breakout whose stop-candle closes strongly opposite → reverse armed
    atr_noise_floor: float = 0.8            # stop distance must exceed this × exec-TF ATR
    sweep_lookback: int = 12                # bars between sweep and BOS
    swing_left: int = 2
    swing_right: int = 2
    stop_buffer_pct: float = 0.0002         # beyond the sweep/swing extreme
    min_stop_pct: float = 0.0005
    max_stop_pct: float = 0.006
    min_rr_to_target: float = 2.0           # take only trades with ≥1:2 room to the next liquidity
    risk_per_trade: float = 1500.0          # ₹ lost if the stop is hit
    max_capital_frac: float = 0.25
    itm_steps: int = 0
    partial_at_r: float = 1.0               # book part, move stop to breakeven
    partial_frac: float = 0.5
    trail: str = "structure"                # "structure": each new protected swing
                                            # "r_ratchet": +1R→breakeven, +2R→lock +1R, +3R→lock +2R, …
    exit_on_opposing_sweep: bool = True     # liquidity taken against us → done
    allow_reverse_scalp: bool = True        # after a profitable CHoCH exit, the same CHoCH may be traded the other way
    max_hold_days: int = 3                  # sessions, entry day included
    overnight_min_r: float = 1.0            # hold overnight only with ≥1R open and stop ≥ breakeven

    @classmethod
    def load(cls, path) -> "AutoConfig":
        if not path:
            return cls()
        import tomllib

        data = tomllib.loads(Path(path).read_text()).get("auto", {})
        known = {f.name for f in fields(cls)}
        bad = set(data) - known
        if bad:
            raise ValueError(f"Unknown [auto] keys: {sorted(bad)}")
        return cls(**data)


@dataclass
class Position:
    contract: OptionContract
    direction: str
    qty: int
    entry_prem: float
    entry_spot: float
    stop_spot: float
    init_stop_spot: float
    target_spot: Optional[float]
    opened: dt.datetime
    entry_idx: int
    setup: str
    sessions: set = field(default_factory=set)
    init_qty: int = 0
    risk_inr: float = 0.0
    partial_done: bool = False
    realised: float = 0.0
    log: list = field(default_factory=list)

    @property
    def risk_pts(self):
        return abs(self.entry_spot - self.init_stop_spot)

    def r_at(self, spot):
        d = spot - self.entry_spot if self.direction == "up" else self.entry_spot - spot
        return d / self.risk_pts if self.risk_pts else 0.0

    def stop_at_or_beyond_be(self):
        return self.stop_spot >= self.entry_spot if self.direction == "up" else self.stop_spot <= self.entry_spot


@dataclass
class ClosedTrade:
    symbol: str
    setup: str
    direction: str
    opened: dt.datetime
    closed: dt.datetime
    qty: int
    entry_prem: float
    pnl: float
    r_multiple: float
    exit_reason: str
    sessions: int


class Engine:
    def __init__(self, cfg: AutoConfig, rules: RuleSet, u: Underlying, broker: Broker,
                 pricer: Callable[[OptionContract, float, dt.datetime], float]):
        self.cfg, self.rules, self.u, self.broker, self.pricer = cfg, rules, u, broker, pricer
        self.ltf = Structure(cfg.swing_left, cfg.swing_right)
        self.htf = Structure(2, 2)
        self._htf_bar: Optional[Candle] = None
        self.guard = Guard(rules)
        self.pos: Optional[Position] = None
        self.closed: list[ClosedTrade] = []
        self.skips: dict = {}
        self.mark_spot = 0.0
        self._pending: Optional[OptionContract] = None
        self.now = dt.datetime.min
        self.windows = [(_t(a), _t(b)) for a, b in cfg.windows]
        self._win_trades: dict = {}
        self._reverse_hint = None  # (direction, bar idx) set by a profitable CHoCH exit
        self.vol = VolatilityEngine()
        self._sma: list = []       # rolling closes for the SMA filter
        self._sar_hint = None      # (direction, bar idx) armed by a failed-breakout stop-out
        self._day = None
        self._day_bar = 0          # bar number within the current day (0 = first candle)

    # paper broker reads this to price the option at the current spot
    def mark(self, symbol: str) -> float:
        c = self.pos.contract if self.pos and self.pos.contract.symbol == symbol else self._pending
        return self.pricer(c, self.mark_spot, self.now)

    def _skip(self, why):
        self.skips[why] = self.skips.get(why, 0) + 1

    # ── main loop ──
    def on_candle(self, c: Candle):
        self.now = c.ts + dt.timedelta(minutes=self.cfg.timeframe)  # decision at candle close
        if c.ts.date() != self._day:
            self._day, self._day_bar = c.ts.date(), 0
        else:
            self._day_bar += 1
        self.vol.update(self.cfg.timeframe, c)
        self._sma.append(c.close)
        if len(self._sma) > self.cfg.sma_period:
            self._sma.pop(0)
        events = self.ltf.update(c)
        self._feed_htf(c)
        i = len(self.ltf.candles) - 1
        if self.pos:
            self._manage(c, events, i)
        if not self.pos:
            self._maybe_enter(c, events, i)

    def _feed_htf(self, c: Candle):
        m = (c.ts.hour * 60 + c.ts.minute - 555) // self.cfg.htf * self.cfg.htf + 555
        key = c.ts.replace(hour=m // 60, minute=m % 60)
        b = self._htf_bar
        if b and b.ts == key:
            b.high, b.low, b.close = max(b.high, c.high), min(b.low, c.low), c.close
        else:
            if b:
                self.htf.update(b)
            self._htf_bar = Candle(key, c.open, c.high, c.low, c.close)

    # ── entries ──
    def _sma_ok(self, d: str, price: float) -> bool:
        if not self.cfg.sma_filter or len(self._sma) < self.cfg.sma_period:
            return True
        m = sum(self._sma) / len(self._sma)
        return price > m if d == "up" else price < m

    def _pdhl_signal(self, c: Candle):
        """PDH/PDL breakout: never the first candle of the day; the candle
        must close beyond the level with a dominant body."""
        dl = self.ltf.day_levels
        rng = c.high - c.low
        if self._day_bar < 1 or rng <= 0:
            return None
        body = abs(c.close - c.open)
        if body / rng < self.cfg.pdhl_body_frac:
            return None
        if dl.get("pdh") and c.close > dl["pdh"] and c.open <= dl["pdh"]:
            return "up", c.low
        if dl.get("pdl") and c.close < dl["pdl"] and c.open >= dl["pdl"]:
            return "down", c.high
        return None

    def _window_of(self, t: dt.time):
        for k, (a, b) in enumerate(self.windows):
            if a <= t < b:
                return k
        return None

    def _maybe_enter(self, c: Candle, events: list[Event], i: int):
        cfg = self.cfg
        win = self._window_of(self.now.time())
        if win is None:
            return
        key = (self.now.date(), win)
        if cfg.max_trades_per_window and self._win_trades.get(key, 0) >= cfg.max_trades_per_window:
            self._skip("session window quota used")
            return
        candidates = [(ev.kind, ev.direction) for ev in events if ev.kind in ("BOS", "CHOCH")]
        if "pdhl_break" in cfg.setups:
            sig = self._pdhl_signal(c)
            if sig:
                candidates.append(("PDHL", sig[0]))
        if self._sar_hint is not None and self._sar_hint[1] == i:
            candidates.append(("SAR", self._sar_hint[0]))
        for kind, d in candidates:
            setup, stop = None, None
            ev = next((e for e in events if e.kind == kind and e.direction == d), None)
            if kind == "PDHL":
                setup, stop = "pdhl_break", self._pdhl_signal(c)[1]
            elif kind == "SAR":
                ref = self.ltf.last_low(untaken=False) if d == "up" else self.ltf.last_high(untaken=False)
                if ref:
                    setup, stop = "stop_and_reverse", ref.price
                self._sar_hint = None
            if not setup and kind in ("BOS", "CHOCH") and "sweep_bos" in cfg.setups:
                sw = [e for e in self.ltf.events if e.kind == "SWEEP" and e.direction == d
                      and i - cfg.sweep_lookback <= e.idx < i]
                if sw:
                    seg = self.ltf.candles[sw[-1].idx: i + 1]
                    stop = min(x.low for x in seg) if d == "up" else max(x.high for x in seg)
                    setup = "sweep_bos"
            if not setup and cfg.allow_reverse_scalp and kind == "CHOCH" and self._reverse_hint == (d, i):
                ref = self.ltf.last_low(untaken=False) if d == "up" else self.ltf.last_high(untaken=False)
                if ref:
                    stop, setup = ref.price, "reverse_scalp"
            if not setup and "trend_bos" in cfg.setups and kind == "BOS" and self.htf.trend == d:
                ref = self.ltf.last_low(untaken=False) if d == "up" else self.ltf.last_high(untaken=False)
                if ref:
                    stop, setup = ref.price, "trend_bos"
            if not setup:
                self._skip("BOS without sweep or HTF alignment")
                continue
            if not self._sma_ok(d, c.close):
                self._skip(f"against the {cfg.sma_period}-SMA")
                continue
            spot = c.close
            buf = spot * cfg.stop_buffer_pct
            stop = stop - buf if d == "up" else stop + buf
            dist = abs(spot - stop)
            if dist < cfg.min_stop_pct * spot:
                self._skip("stop too tight")
                continue
            if dist > cfg.max_stop_pct * spot:
                self._skip("stop too wide")
                continue
            a = self.vol.atr(cfg.timeframe)
            if a and dist < cfg.atr_noise_floor * a:
                self._skip("stop inside normal noise (ATR floor)")
                continue
            # aim at higher-timeframe liquidity (HTF swings, previous-day high/low)
            target = self.htf.next_target(d, spot)
            if target and abs(target - spot) / dist < cfg.min_rr_to_target:
                self._skip("next liquidity too close")
                continue
            self._open(d, spot, stop, target, setup, i, win_key=key)
            return

    def _open(self, d, spot, stop, target, setup, i, win_key=None):
        cfg, u = self.cfg, self.u
        con = choose_contract(u, spot, d, self.now, cfg.max_hold_days, cfg.itm_steps)
        entry = self.pricer(con, spot, self.now)
        stop_prem = self.pricer(con, stop, self.now)
        risk_budget = cfg.risk_per_trade * self.guard.risk_scale()
        lots = size_lots(entry, stop_prem, u.lot, risk_budget, self.rules.max_lots,
                         self.rules.capital, cfg.max_capital_frac)
        if lots < max(1, self.rules.min_lots):
            self._skip("minimum position cannot fit inside the risk budget" if lots else "risk too large for 1 lot")
            return
        qty = lots * u.lot
        dec = self.guard.check(con.symbol, "Buy", qty, entry, self.now, risk=(entry - stop_prem) * qty)
        if not dec:
            self._skip("guard: " + dec.reasons[0].split(".")[0])
            return
        self._pending, self.mark_spot = con, spot
        f = self.broker.place_market(con.symbol, "Buy", qty, self.now)
        self.guard.on_fill(con.symbol, "Buy", qty, f.price, self.now)
        if win_key is not None:
            self._win_trades[win_key] = self._win_trades.get(win_key, 0) + 1
        self._reverse_hint = None
        self.pos = Position(con, d, qty, f.price, spot, stop, stop, target, self.now, i, setup, {self.now.date()},
                            init_qty=qty, risk_inr=(f.price - stop_prem) * qty)
        self.pos.log.append(f"{self.now:%d %b %H:%M} {setup} entry {qty} @ {f.price} spot {spot:.1f} stop {stop:.1f}")

    # ── management ──
    def _sell(self, qty, spot, reason):
        p = self.pos
        self.mark_spot = spot
        f = self.broker.place_market(p.contract.symbol, "Sell", qty, self.now)
        self.guard.on_fill(p.contract.symbol, "Sell", qty, f.price, self.now)
        p.realised += (f.price - p.entry_prem) * qty
        p.qty -= qty
        p.log.append(f"{self.now:%d %b %H:%M} {reason}: sell {qty} @ {f.price} spot {spot:.1f}")
        if p.qty == 0:
            self.guard.on_close(p.contract.symbol, p.realised, self.now)
            init_risk_inr = max(1.0, p.risk_inr)
            self.closed.append(ClosedTrade(p.contract.symbol, p.setup, p.direction, p.opened, self.now,
                                           p.init_qty, p.entry_prem, p.realised,
                                           p.realised / init_risk_inr, reason, len(p.sessions)))
            self.pos = None

    def _manage(self, c: Candle, events: list[Event], i: int):
        p, cfg = self.pos, self.cfg
        p.sessions.add(c.ts.date())
        up = p.direction == "up"
        # 1. stop (touch), gap-aware
        hit = c.low <= p.stop_spot if up else c.high >= p.stop_spot
        if hit:
            was_initial = not p.stop_at_or_beyond_be()
            setup_was = p.setup
            px = min(c.open, p.stop_spot) if up else max(c.open, p.stop_spot)
            self._sell(p.qty, px, "trail stop" if not was_initial else "stop loss")
            rng = c.high - c.low
            strong_opposite = rng > 0 and abs(c.close - c.open) / rng >= 0.6 and \
                ((up and c.close < c.open) or (not up and c.close > c.open))
            if self.cfg.stop_and_reverse and was_initial and setup_was == "pdhl_break" and strong_opposite:
                self._sar_hint = ("down" if up else "up", i)
            return
        # 2. partial at +1R, stop to breakeven
        lvl = p.entry_spot + (1 if up else -1) * cfg.partial_at_r * p.risk_pts
        if not p.partial_done and (c.high >= lvl if up else c.low <= lvl):
            q = (int(p.qty * cfg.partial_frac) // self.u.lot) * self.u.lot
            if q:
                self._sell(q, lvl, f"partial at {cfg.partial_at_r:g}R")
            p.partial_done = True
            p.stop_spot = max(p.stop_spot, p.entry_spot) if up else min(p.stop_spot, p.entry_spot)
            if not self.pos:
                return
        # 3a. R-ratchet trail: once +kR trades (k≥1), the stop locks +(k−1)R
        if cfg.trail == "r_ratchet":
            r_hi = p.r_at(c.high if up else c.low)
            k = int(r_hi)
            if k >= 1:
                lock = p.entry_spot + (1 if up else -1) * (k - 1) * p.risk_pts
                if (up and lock > p.stop_spot) or (not up and lock < p.stop_spot):
                    p.stop_spot = lock
                    p.log.append(f"{self.now:%d %b %H:%M} ratchet +{k}R hit → stop locks {'+' if k > 1 else ''}{k - 1}R ({lock:.1f})")
        # 3b. ride: trail to each new protected swing after a BOS our way
        for ev in events:
            if ev.kind == "BOS" and ev.direction == p.direction and cfg.trail == "structure":
                sw = [s for s in (self.ltf.lows if up else self.ltf.highs) if s.idx > p.entry_idx - cfg.swing_right]
                if sw:
                    new = sw[-1].price - (c.close * cfg.stop_buffer_pct) * (1 if up else -1)
                    if (up and new > p.stop_spot) or (not up and new < p.stop_spot):
                        p.stop_spot = new
                        p.log.append(f"{self.now:%d %b %H:%M} trail → {new:.1f}")
            # 4. structure flips against us on close
            if ev.kind == "CHOCH" and ev.direction != p.direction:
                was_green = p.realised + (self.pricer(p.contract, c.close, self.now) - p.entry_prem) * p.qty > 0
                self._sell(p.qty, c.close, "CHoCH against")
                if was_green and self.cfg.allow_reverse_scalp:
                    self._reverse_hint = (ev.direction, i)
                return
            # 5. opposing liquidity swept → the move is likely done
            if cfg.exit_on_opposing_sweep and ev.kind == "SWEEP" and ev.direction != p.direction and p.r_at(c.close) > 0:
                self._sell(p.qty, c.close, "opposing liquidity swept")
                return
        # 6. session end
        if self.now.time() >= _t(cfg.eod_exit):
            if len(p.sessions) >= cfg.max_hold_days:
                self._sell(p.qty, c.close, f"time stop ({cfg.max_hold_days} sessions)")
            elif (p.contract.expiry - self.now.date()).days <= 1:
                self._sell(p.qty, c.close, "expiry next day")
            elif not (p.r_at(c.close) >= cfg.overnight_min_r and p.stop_at_or_beyond_be()):
                self._sell(p.qty, c.close, "EOD (not enough cushion to hold overnight)")

    def force_close(self, spot: float, reason="end of data"):
        if self.pos:
            self._sell(self.pos.qty, spot, reason)
