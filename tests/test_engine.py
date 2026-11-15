import datetime as dt

import pytest

from pie_trader.detectors import detect
from pie_trader.fmt import inr
from pie_trader.models import Trade
from pie_trader.pnl import build_episodes, dedupe_by_trade_id
from pie_trader.rules import Guard, RuleSet, replay

D = dt.date(2026, 11, 10)
SYM = "NIFTY 17 NOV 2026 25000 CE"
EXP_SYM = "NIFTY 10 NOV 2026 25000 CE"  # expires on D


def T(side, qty, px, seq, sym=SYM, day=D):
    return Trade(day, "NSEFO", side, sym, qty, px, seq=seq)


def test_fifo_and_episodes():
    tr = [T("Buy", 65, 100, 0.1), T("Buy", 65, 80, 0.2), T("Sell", 130, 90, 0.3),
          T("Buy", 65, 50, 0.4), T("Sell", 65, 60, 0.5)]
    eps = build_episodes(tr)
    assert len(eps) == 2
    assert eps[0].pnl == pytest.approx(65 * -10 + 65 * 10)
    assert eps[0].max_qty == 130 and eps[0].adds_below_avg == 1
    assert eps[1].pnl == pytest.approx(650)
    assert sum(sum(e.entry_pnl) for e in eps) == pytest.approx(sum(e.pnl for e in eps))


def test_flip_through_zero_splits_episode():
    eps = build_episodes([T("Buy", 65, 100, 0.1), T("Sell", 130, 110, 0.2), T("Buy", 65, 105, 0.3)])
    assert [e.direction for e in eps] == ["L", "S"]
    assert eps[0].pnl == pytest.approx(650) and eps[1].pnl == pytest.approx(325)


def test_open_position_kept_out_of_replay():
    eps = build_episodes([T("Buy", 65, 100, 0.1)])
    assert eps[0].is_open
    assert replay(eps, RuleSet()).original == 0


def test_replay_blocks_expiry_day_and_caps_size():
    tr = [T("Buy", 650, 100, 0.1), T("Sell", 650, 90, 0.2),            # 10 lots, -6500
          T("Buy", 65, 100, 0.5, EXP_SYM), T("Sell", 65, 50, 0.6, EXP_SYM)]  # expiry day, -3250
    rp = replay(build_episodes(tr), RuleSet(cooldown_minutes=0))
    assert rp.original == pytest.approx(-9750)
    assert rp.disciplined == pytest.approx(-6500 * 2 / 10)
    assert "Expiry-day entry" in rp.blocked


def test_replay_removes_averaging_adds():
    tr = [T("Buy", 65, 100, 0.1), T("Buy", 65, 50, 0.2), T("Sell", 130, 40, 0.3)]
    rp = replay(build_episodes(tr), RuleSet())
    assert rp.disciplined == pytest.approx(65 * -60)


def test_daily_stop_and_streak():
    tr = []
    for i in range(5):
        tr += [T("Buy", 65, 100, 0.1 + i * 0.1), T("Sell", 65, 70, 0.15 + i * 0.1)]  # -1950 each
    rp = replay(build_episodes(tr), RuleSet(cooldown_minutes=0, max_consecutive_losses=99))
    assert len(rp.kept) == 3  # 3rd loss crosses -4000 → stopped
    rp = replay(build_episodes(tr), RuleSet(cooldown_minutes=0, max_daily_loss=1e9))
    assert len(rp.kept) == 2


def test_detectors_find_core_mistakes():
    tr = [T("Buy", 65, 100, 0.05, "NIFTY 24 NOV 2026 24000 PE"), T("Sell", 65, 101, 0.06, "NIFTY 24 NOV 2026 24000 PE"),
          T("Buy", 65, 100, 0.1), T("Sell", 65, 60, 0.2),
          T("Buy", 650, 100, 0.3), T("Buy", 65, 70, 0.35), T("Sell", 715, 50, 0.4),
          T("Buy", 65, 100, 0.5, EXP_SYM), T("Sell", 65, 10, 0.6, EXP_SYM)]
    codes = {f.code for f in detect(tr, build_episodes(tr), RuleSet())}
    assert {"EXPIRY_DAY", "OVERSIZE", "REVENGE", "AVERAGING", "PAYOFF"} <= codes


def test_guard():
    g = Guard(RuleSet())
    t0 = dt.datetime(2026, 11, 10, 10, 0)
    assert g.check(SYM, "Buy", 65, 60, t0)
    assert not g.check(SYM, "Buy", 195, 60, t0)                 # > 2 lots
    assert not g.check(EXP_SYM, "Buy", 65, 20, t0)              # expiry day
    g.on_fill(SYM, "Buy", 65, 60, t0)
    assert not g.check(SYM, "Buy", 65, 50, t0)                  # averaging down
    g.on_fill(SYM, "Sell", 65, 40, t0)
    g.on_close(SYM, -1300, t0)
    d = g.check(SYM, "Buy", 65, 60, t0 + dt.timedelta(minutes=5))
    assert not d and "Cooldown" in d.reasons[0]
    assert g.check(SYM, "Buy", 65, 60, t0 + dt.timedelta(minutes=20))
    g.on_close(SYM, -3000, t0)
    assert not g.check(SYM, "Buy", 65, 60, t0 + dt.timedelta(hours=1))  # daily loss + streak


def test_rules_toml_loads(tmp_path):
    p = tmp_path / "r.toml"
    p.write_text("[risk]\nmax_lots = 1\n[lot_sizes]\nNIFTY = 75\n")
    r = RuleSet.load(p)
    assert r.max_lots == 1 and r.lot_size("NIFTY") == 75 and r.lot_size("SENSEX") == 20
    p.write_text("[risk]\nbogus = 1\n")
    with pytest.raises(ValueError):
        RuleSet.load(p)


def test_inr():
    assert inr(-168540) == "−₹1,68,540"
    assert inr(1035) == "+₹1,035"
    assert inr(-127216, compact=True) == "−₹1.27L"


def test_duplicate_trade_id_does_not_double_count():
    tr = [T("Buy", 65, 100, 0.1), T("Sell", 65, 60, 0.2)]
    tr[0].trade_id = "5380839"
    tr[1].trade_id = "5349148"
    dup = T("Buy", 65, 100, 0.1)
    dup.trade_id = "5380839"  # same broker trade id, e.g. a re-run import
    eps = build_episodes(tr + [dup])
    assert len(eps) == 1
    assert eps[0].pnl == pytest.approx(65 * -40)  # unaffected by the duplicate


def test_dedupe_by_trade_id_counts_drops_and_keeps_blank_ids():
    tr = [T("Buy", 65, 100, 0.1), T("Sell", 65, 60, 0.2)]
    tr[0].trade_id = "1"
    tr[1].trade_id = "1"  # distinct trades that just happen to share no id — blank below instead
    blanks = [T("Buy", 1, 1, 0.3), T("Buy", 1, 1, 0.4)]  # trade_id="" by default, never "duplicates" of each other
    dup = T("Buy", 65, 100, 0.1)
    dup.trade_id = "1"
    out, dropped = dedupe_by_trade_id([tr[0], dup] + blanks)
    assert dropped == 1
    assert len(out) == 3
