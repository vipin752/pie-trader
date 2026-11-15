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
    args = ap.parse_args(argv)

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


if __name__ == "__main__":
    main()
