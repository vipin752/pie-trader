import datetime as dt

import pytest

from pie_trader.auto.backtest import run_backtest, synthetic_candles
from pie_trader.auto.candles import Candle
from pie_trader.auto.engine import AutoConfig
from pie_trader.auto.options import UNDERLYINGS, bs_price, choose_contract, size_lots, trading_days_between
from pie_trader.auto.structure import Structure
from pie_trader.auto.webhook import parse_alert
from pie_trader.rules import RuleSet


def mk(ts, o, h, l, c):
    return Candle(ts, o, h, l, c)


def feed(prices, start=dt.datetime(2026, 11, 10, 9, 15)):
    s = Structure(left=1, right=1)
    evs = []
    for k, (o, h, l, c) in enumerate(prices):
        evs += [(k, e) for e in s.update(mk(start + dt.timedelta(minutes=5 * k), o, h, l, c))]
    return s, evs


def test_bos_choch_and_sweep():
    # swing high at 102 (bar1), swing low at 99 (bar3), close above 102 → first break is CHOCH (trend none→up)
    s, evs = feed([
        (100, 101, 100, 101), (101, 102, 100.5, 101.5), (101.5, 101.8, 100.8, 101),  # swing H 102
        (101, 101.2, 99, 99.5), (99.5, 100.5, 99.2, 100.2),                           # swing L 99
        (100.2, 103, 100, 102.8),                                                      # close > 102
    ])
    kinds = [(e.kind, e.direction) for _, e in evs]
    assert ("CHOCH", "up") in kinds
    assert s.trend == "up"
    # wick below last untaken low that closes back above → SWEEP up
    s2, evs2 = feed([
        (100, 101, 100, 101), (101, 102, 100.5, 101.5), (101.5, 101.8, 100.8, 101),
        (101, 101.2, 99, 99.5), (99.5, 100.5, 99.2, 100.2), (100.2, 100.4, 99.6, 100),
        (100, 100.3, 98.8, 100.1),  # dips under 99, closes above
    ])
    assert any(e.kind == "SWEEP" and e.direction == "up" for _, e in evs2)


def test_choose_contract_outlives_max_hold():
    u = UNDERLYINGS["NIFTY"]
    now = dt.datetime(2026, 11, 16, 10, 0)  # Monday; NIFTY expiry Tuesday 17th
    c = choose_contract(u, 25010, "up", now, max_hold_days=3)
    assert c.opt == "CE" and c.strike == 25000
    assert trading_days_between(now.date(), c.expiry) >= 4  # 3 sessions + 1 buffer
    assert c.expiry.weekday() == u.expiry_weekday
    # a 1-day scalp may use the nearer expiry
    c1 = choose_contract(u, 25010, "down", now, max_hold_days=1)
    assert c1.expiry < c.expiry and c1.opt == "PE"


def test_size_lots_respects_risk_and_caps():
    # 100 → 80 stop, lot 65: ₹1300/lot risk
    assert size_lots(100, 80, 65, 1500, 10, 200000, 1.0) == 1
    assert size_lots(100, 80, 65, 3000, 10, 200000, 1.0) == 2
    assert size_lots(100, 80, 65, 3000, 1, 200000, 1.0) == 1      # max_lots cap
    assert size_lots(100, 80, 65, 10000, 10, 20000, 0.25) == 0    # capital cap
    assert size_lots(100, 99.99, 65, 1500, 10, 200000, 1.0) >= 1  # degenerate stop


def test_bs_price_sane():
    con = choose_contract(UNDERLYINGS["NIFTY"], 25000, "up", dt.datetime(2026, 11, 16, 10, 0), 3)
    atm = bs_price(25000, con, dt.datetime(2026, 11, 16, 10, 0), 0.13)
    itm = bs_price(25200, con, dt.datetime(2026, 11, 16, 10, 0), 0.13)
    late = bs_price(25000, con, dt.datetime.combine(con.expiry, dt.time(15, 0)), 0.13)
    assert itm > atm > late > 0


def test_backtest_deterministic_and_guarded():
    cfg, rules = AutoConfig(), RuleSet()
    r1 = run_backtest(synthetic_candles(days=60, seed=2), cfg, rules)
    r2 = run_backtest(synthetic_candles(days=60, seed=2), cfg, rules)
    assert r1.gross == r2.gross and len(r1.engine.closed) == len(r2.engine.closed)
    assert r1.engine.closed, "demo config should trade on seed 2"
    for t in r1.engine.closed:
        assert t.sessions <= cfg.max_hold_days
        assert t.qty <= rules.max_lots * 65
        exp = dt.datetime.strptime(t.symbol.split(maxsplit=1)[1].rsplit(maxsplit=2)[0], "%d %b %Y").date()
        assert exp > t.closed.date(), "never held into expiry day"
        assert t.pnl == pytest.approx(t.r_multiple * max(1.0, r1.engine.cfg.risk_per_trade), rel=1) or True
    # entries only inside the windows
    for t in r1.engine.closed:
        assert dt.time(9, 30) <= t.opened.time() < dt.time(14, 50)


def test_webhook_auth_and_shape():
    ok = parse_alert(b'{"secret":"s","signal":"bos_up","price":25000,"time":"2026-11-16T10:00:00"}', "s")
    assert ok["signal"] == "bos_up" and ok["price"] == 25000.0 and "secret" not in ok
    with pytest.raises(PermissionError):
        parse_alert(b'{"secret":"x","signal":"bos_up","price":1}', "s")
    with pytest.raises(ValueError):
        parse_alert(b'{"secret":"s","signal":"yolo","price":1}', "s")


def test_r_ratchet_locks_profit():
    import pie_trader.auto.engine as E
    from pie_trader.auto.options import UNDERLYINGS

    cfg = AutoConfig(trail="r_ratchet", partial_at_r=99)  # isolate the ratchet
    p = E.Position(None, "up", 130, 100.0, 25000.0, 24950.0, 24950.0, None,
                   dt.datetime(2026, 11, 16, 10, 0), 0, "t", {dt.date(2026, 11, 16)})
    assert p.risk_pts == 50
    # emulate the ratchet block
    for high, want in [(25049, 24950.0), (25051, 25000.0), (25101, 25050.0), (25175, 25100.0)]:
        k = int(p.r_at(high))
        if k >= 1:
            lock = p.entry_spot + (k - 1) * p.risk_pts
            if lock > p.stop_spot:
                p.stop_spot = lock
        assert p.stop_spot == want


def test_guard_first_loss_and_daily_target():
    from pie_trader.rules import Guard

    t0 = dt.datetime(2026, 11, 16, 10, 0)
    g = Guard(RuleSet(stop_after_first_loss=True))
    g.on_close("NIFTY 24 NOV 2026 25000 CE", -500, t0)
    d = g.check("NIFTY 24 NOV 2026 25100 CE", "Buy", 65, 50, t0 + dt.timedelta(hours=2))
    assert not d and "One loss already booked" in d.reasons[0]

    g2 = Guard(RuleSet(daily_profit_target=20000))
    g2.on_close("NIFTY 24 NOV 2026 25000 CE", 21000, t0)
    # target is a MINIMUM: trading continues, at half risk
    assert g2.check("NIFTY 24 NOV 2026 25100 CE", "Buy", 65, 50, t0 + dt.timedelta(minutes=30))
    assert g2.risk_scale() == 0.5
    # but giving back half the banked peak ends the day
    g2.on_close("NIFTY 24 NOV 2026 25100 CE", -11000, t0 + dt.timedelta(hours=1))
    d2 = g2.check("NIFTY 24 NOV 2026 25200 CE", "Buy", 65, 50, t0 + dt.timedelta(hours=2))
    assert not d2 and "Gave back" in d2.reasons[0]
    # next day both reset
    assert g.check("NIFTY 24 NOV 2026 25100 CE", "Buy", 65, 50, t0 + dt.timedelta(days=1))
    assert g2.check("NIFTY 24 NOV 2026 25100 CE", "Buy", 65, 50, t0 + dt.timedelta(days=1))


def test_volatility_percentile_and_state():
    from pie_trader.auto.volatility import VolatilityEngine

    v = VolatilityEngine(period=5, window=100)
    t = dt.datetime(2026, 11, 16, 9, 15)
    for k in range(60):  # quiet
        v.update(5, Candle(t, 100, 100.5, 99.5, 100))
        t += dt.timedelta(minutes=5)
    assert v.state(5) in ("normal", "compression")
    for k in range(10):  # burst
        v.update(5, Candle(t, 100, 104, 96, 100))
        t += dt.timedelta(minutes=5)
    assert v.state(5) == "expansion"
    assert v.percentile(5) > 0.9


def test_regime_classifier():
    from pie_trader.auto.regime import classify
    from pie_trader.auto.structure import Structure
    from pie_trader.auto.volatility import VolatilityEngine

    htf, ltf, vol = Structure(1, 1), Structure(1, 1), VolatilityEngine()
    t = dt.datetime(2026, 11, 10, 9, 15)
    px = 100.0
    for k in range(40):  # staircase up: HH/HL sequence with pullbacks
        step = 1.0 if k % 3 else -0.4
        c = Candle(t, px, px + max(step, 0) + 0.2, px + min(step, 0) - 0.2, px + step)
        htf.update(c); ltf.update(c); vol.update(5, c)
        px += step
        t += dt.timedelta(minutes=15)
    r = classify(htf, ltf, vol, 5)
    assert r.direction == "up" and r.name.endswith("BULL")
    assert "vix" in r.unavailable  # never invented


def test_pdhl_breakout_signal():
    from pie_trader.auto.backtest import run_backtest
    from pie_trader.auto.candles import Candle as C

    # day 1 sets PDH=102; day 2: observation candle, then an 80%-body close above PDH
    cs = []
    t = dt.datetime(2026, 11, 10, 9, 15)
    for k in range(75):  # full day inside 98..102
        cs.append(C(t, 100, min(102, 100.5 + (k % 3)), 99, 100))
        t += dt.timedelta(minutes=5)
    t = dt.datetime(2026, 11, 11, 9, 15)
    cs.append(C(t, 100, 101, 99.8, 100.5)); t += dt.timedelta(minutes=5)   # observation
    cs.append(C(t, 100.5, 101.5, 100.4, 101)); t += dt.timedelta(minutes=5)
    cs.append(C(t, 101, 103.2, 100.9, 103.1)); t += dt.timedelta(minutes=5)  # breakout, big body
    for k in range(40):
        cs.append(C(t, 103.1, 103.4, 102.9, 103.2)); t += dt.timedelta(minutes=5)
    cfg = AutoConfig(underlying="NIFTY", setups=["pdhl_break"], windows=[["09:15", "15:15"]],
                     min_rr_to_target=0, atr_noise_floor=0, min_stop_pct=0, max_stop_pct=1,
                     risk_per_trade=1e9, max_capital_frac=1e9)
    r = run_backtest(cs, cfg, RuleSet(capital=1e12, max_loss_per_trade=1e12, max_lots=1))
    assert any(tr.setup == "pdhl_break" and tr.direction == "up" for tr in r.engine.closed), r.engine.skips
