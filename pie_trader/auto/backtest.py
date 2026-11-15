"""Run the engine over historical underlying candles with a paper broker."""
from __future__ import annotations

import random
import datetime as dt
import statistics
from dataclasses import dataclass

from ..pnl import estimate_charges
from ..rules import RuleSet
from .broker import PaperBroker, fills_to_trades
from .candles import Candle
from .engine import AutoConfig, Engine
from .instruments import InstrumentMaster
from .options import bs_price


@dataclass
class BacktestResult:
    engine: Engine
    trades: list          # pie_trader.models.Trade, for the analyser/report
    gross: float
    charges: float

    @property
    def net(self):
        return self.gross - self.charges

    def summary(self) -> dict:
        ct = self.engine.closed
        if not ct:
            return {"trades": 0, "skips": self.engine.skips}
        wins = [t for t in ct if t.pnl > 0]
        eq, peak, dd = 0.0, 0.0, 0.0
        for t in ct:
            eq += t.pnl
            peak = max(peak, eq)
            dd = min(dd, eq - peak)
        reasons: dict = {}
        for t in ct:
            r = reasons.setdefault(t.exit_reason, [0, 0.0])
            r[0] += 1
            r[1] += t.pnl
        return {
            "trades": len(ct),
            "win_rate": len(wins) / len(ct),
            "avg_r": statistics.mean(t.r_multiple for t in ct),
            "best_r": max(t.r_multiple for t in ct),
            "worst_r": min(t.r_multiple for t in ct),
            "gross": self.gross, "charges": self.charges, "net": self.net,
            "max_drawdown": dd,
            "held_overnight": sum(t.sessions > 1 for t in ct),
            "by_setup": {s: round(sum(t.pnl for t in ct if t.setup == s)) for s in {t.setup for t in ct}},
            "exit_reasons": {k: (v[0], round(v[1])) for k, v in reasons.items()},
            "skips": self.engine.skips,
        }


def run_backtest(candles: list[Candle], cfg: AutoConfig, rules: RuleSet, master: InstrumentMaster | None = None,
                 portfolio=None) -> BacktestResult:
    u = (master or InstrumentMaster()).get(cfg.underlying)
    u_iv = u.iv
    pricer = lambda con, spot, ts: bs_price(spot, con, ts, u_iv)
    holder = {}
    broker = PaperBroker(lambda sym: holder["e"].mark(sym))
    eng = Engine(cfg, rules, u, broker, pricer, portfolio=portfolio)
    holder["e"] = eng
    for c in candles:
        eng.on_candle(c)
    if candles:
        eng.force_close(candles[-1].close)
    trades = fills_to_trades(broker.fills())
    gross = sum(t.pnl for t in eng.closed)
    return BacktestResult(eng, trades, gross, estimate_charges(trades))


def synthetic_candles(days=40, start=dt.date(2026, 9, 1), spot=25000.0, tf=5, seed=7) -> list[Candle]:
    """Regime-switching random walk with liquidity-hunting wicks. For tests
    and demos only. Real decisions need real data."""
    rnd = random.Random(seed)
    out, d = [], start
    while len([1 for _ in {c.ts.date() for c in out}]) < days:
        if d.weekday() < 5:
            drift = rnd.choice([-1, 0, 1]) * spot * 0.00015
            t = dt.datetime.combine(d, dt.time(9, 15))
            spot *= 1 + rnd.gauss(0, 0.003)  # overnight gap
            while t.time() < dt.time(15, 30):
                o = spot
                c = o + drift + rnd.gauss(0, spot * 0.0009)
                hi = max(o, c) + abs(rnd.gauss(0, spot * 0.0004))
                lo = min(o, c) - abs(rnd.gauss(0, spot * 0.0004))
                if rnd.random() < 0.04:  # stop hunt wick
                    if rnd.random() < 0.5:
                        hi += spot * 0.0015
                    else:
                        lo -= spot * 0.0015
                out.append(Candle(t, round(o, 2), round(hi, 2), round(lo, 2), round(c, 2)))
                spot = c
                t += dt.timedelta(minutes=tf)
                if rnd.random() < 0.02:
                    drift = rnd.choice([-1, 0, 1]) * spot * 0.00015
        d += dt.timedelta(days=1)
    return out


def run_portfolio_backtest(indices: dict, master: "InstrumentMaster | None" = None,
                           portfolio=None) -> dict:
    """Backtest several indices together, stepping every Engine in one shared
    chronological order so a PortfolioGuard's "already open" state is honoured
    across indices in real time rather than per-index in isolation.

    `indices`: {name: (candles, AutoConfig, RuleSet)}. Each AutoConfig.underlying
    must match its dict key. Returns {name: BacktestResult}.
    """
    from .portfolio import PortfolioGuard

    master = master or InstrumentMaster()
    portfolio = portfolio if portfolio is not None else PortfolioGuard()
    engines, results = {}, {}
    events = []  # (timestamp, name, candle)
    for name, (candles, cfg, rules) in indices.items():
        if cfg.underlying != name:
            raise ValueError(f"indices[{name!r}].underlying is {cfg.underlying!r}, must match its key")
        u = master.get(name)
        pricer = lambda con, spot, ts, _iv=u.iv: bs_price(spot, con, ts, _iv)
        holder = {}
        broker = PaperBroker(lambda sym, _h=holder: _h["e"].mark(sym))
        eng = Engine(cfg, rules, u, broker, pricer, portfolio=portfolio)
        holder["e"] = eng
        engines[name] = (eng, broker, candles)
        events += [(c.ts, name, c) for c in candles]
    events.sort(key=lambda e: e[0])
    for _, name, c in events:
        engines[name][0].on_candle(c)
    for name, (eng, broker, candles) in engines.items():
        if candles:
            eng.force_close(candles[-1].close)
        trades = fills_to_trades(broker.fills())
        gross = sum(t.pnl for t in eng.closed)
        results[name] = BacktestResult(eng, trades, gross, estimate_charges(trades))
    return results
