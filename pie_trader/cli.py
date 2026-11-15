"""pie — analyse a trade history and produce a discipline report.

    pie analyse trades.xlsx [--rules rules.toml] [--out report.html] [--json findings.json]
"""
from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict

from .detectors import detect
from .parsers import load_trades
from .pnl import build_episodes, estimate_charges
from .fmt import inr
from .report import render
from .rules import RuleSet, replay, rule_impact


def main(argv=None):
    ap = argparse.ArgumentParser(prog="pie")
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("analyse", aliases=["analyze"], help="Analyse a trade history")
    a.add_argument("file")
    a.add_argument("--rules", default=None)
    a.add_argument("--out", default="pie-report.html")
    a.add_argument("--json", default=None)
    b = sub.add_parser("backtest", help="Backtest the auto engine on underlying candles")
    b.add_argument("candles", nargs="?", help="CSV of underlying candles (datetime,open,high,low,close). Omit for a synthetic demo.")
    b.add_argument("--config", default=None, help="TOML with an [auto] section")
    b.add_argument("--rules", default=None)
    b.add_argument("--report", default=None, help="Also run the discipline report on the bot's fills")

    w = sub.add_parser("webhook", help="TradingView webhook listener (signals only; paper unless a live broker is configured)")
    w.add_argument("--secret", required=True)
    w.add_argument("--port", type=int, default=8787)
    args = ap.parse_args(argv)

    if args.cmd == "backtest":
        return _backtest(args)
    if args.cmd == "webhook":
        return _webhook(args)

    rules = RuleSet.load(args.rules)
    trades = load_trades(args.file)
    if not trades:
        sys.exit("No trades found.")
    eps = build_episodes(trades)
    findings = detect(trades, eps, rules)
    rp = replay(eps, rules)
    impacts = rule_impact(eps, rules)
    ch = estimate_charges(trades)

    print(f"Orders {len(trades)} · positions {len(eps)} · gross {inr(rp.original)} · charges ≈{inr(ch, sign=False)} · net {inr(rp.original - ch)}")
    print(f"With rules: gross {inr(rp.disciplined)}  (avoided {inr(rp.saved)})\n")
    for f in findings:
        print(f"[{f.severity.upper():8}] {f.title}: {f.summary}\n           → {f.fix}")
    with open(args.out, "w") as fh:
        fh.write(render(trades, eps, findings, rp, rules, impacts))
    print(f"\nReport: {args.out}")
    if args.json:
        with open(args.json, "w") as fh:
            json.dump({"gross": rp.original, "charges": ch, "disciplined_gross": rp.disciplined,
                       "findings": [asdict(f) for f in findings],
                       "rule_impact": impacts}, fh, indent=2, default=str)




def _backtest(args):
    from .auto.backtest import run_backtest, synthetic_candles
    from .auto.candles import load_candles
    from .auto.engine import AutoConfig

    cfg = AutoConfig.load(args.config)
    rules = RuleSet.load(args.rules)
    if args.candles:
        candles = load_candles(args.candles)
    else:
        print("No candle file given — running the SYNTHETIC demo. Results mean nothing for real markets.\n")
        candles = synthetic_candles(days=60, seed=2)
    res = run_backtest(candles, cfg, rules)
    s = res.summary()
    if not s["trades"]:
        print("No trades taken. Skips:", s["skips"])
        return
    print(f"Trades {s['trades']} · win rate {s['win_rate']:.0%} · avg {s['avg_r']:+.2f}R · "
          f"gross {inr(s['gross'])} · charges {inr(s['charges'], sign=False)} · net {inr(s['net'])}")
    print(f"Max drawdown {inr(s['max_drawdown'])} · held overnight {s['held_overnight']}/{s['trades']}")
    print("By setup:", {k: inr(v) for k, v in s["by_setup"].items()})
    print("Exits:", {k: f"{n}× {inr(p)}" for k, (n, p) in s["exit_reasons"].items()})
    print("Skipped entries:", s["skips"])
    for t in res.engine.closed:
        print(f"  {t.opened:%d %b %H:%M} {t.setup:9} {t.symbol:32} {t.exit_reason:34} "
              f"{t.sessions}d {inr(t.pnl):>12} {t.r_multiple:+.2f}R")
    if args.report:
        from .detectors import detect
        from .pnl import build_episodes
        from .rules import replay, rule_impact
        from .report import render
        epi = build_episodes(res.trades)
        with open(args.report, "w") as fh:
            fh.write(render(res.trades, epi, detect(res.trades, epi, rules), replay(epi, rules), rules, rule_impact(epi, rules)))
        print("Report:", args.report)


def _webhook(args):
    from .auto.webhook import serve

    def handler(alert):
        print("ALERT", alert)
        return {"ok": True, "note": "paper mode: no live broker configured", "alert": alert}

    serve(handler, args.secret, args.port)


if __name__ == "__main__":
    main()
