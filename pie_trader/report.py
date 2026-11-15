"""Self-contained HTML report rendered with the PIE design system."""
from __future__ import annotations

import html
import json
from collections import Counter
from pathlib import Path

from .detectors import Finding
from .pnl import estimate_charges
from .fmt import inr
from .rules import RuleSet, ReplayResult

CSS = (Path(__file__).resolve().parent.parent / "design" / "pie.css")
SEV = {"critical": ("■", "Critical"), "high": ("▲", "High"), "medium": ("●", "Medium"), "low": ("○", "Low")}


def money(v, **kw):
    cls = "pos" if v > 0 else "neg" if v < 0 else ""
    return f'<span class="num {cls}">{inr(v, **kw)}</span>'


def _esc(x):
    return html.escape(str(x))


def _line_chart(days, series, h=260):
    """series: list of (label, css_var, values)."""
    W, L, R, T, B = 960, 64, 16, 12, 28
    vals = [v for _, _, s in series for v in s] + [0]
    lo, hi = min(vals), max(vals)
    pad = (hi - lo) * 0.08 or 1
    lo, hi = lo - pad, hi + pad
    x = lambda i: L + (W - L - R) * (i / max(1, len(days) - 1))
    y = lambda v: T + (h - T - B) * (1 - (v - lo) / (hi - lo))
    ticks = _ticks(lo, hi)
    g = "".join(f'<line x1="{L}" x2="{W - R}" y1="{y(t):.1f}" y2="{y(t):.1f}"/>' for t in ticks)
    ax = "".join(f'<text x="{L - 8}" y="{y(t) + 4:.1f}" text-anchor="end">{inr(t, compact=True)}</text>' for t in ticks)
    step = max(1, len(days) // 8)
    ax += "".join(f'<text x="{x(i):.1f}" y="{h - 6}" text-anchor="middle">{d.strftime("%d %b")}</text>'
                  for i, d in enumerate(days) if i % step == 0)
    paths = ""
    for label, var, s in series:
        pts = " ".join(f"{x(i):.1f},{y(v):.1f}" for i, v in enumerate(s))
        paths += f'<polyline fill="none" stroke="var({var})" stroke-width="2" stroke-linejoin="round" points="{pts}"/>'
        lx, ly = x(len(s) - 1), y(s[-1])
        paths += f'<circle cx="{lx:.1f}" cy="{ly:.1f}" r="4" fill="var({var})" stroke="var(--surface-1)" stroke-width="2"/>'
    data = json.dumps({"days": [d.strftime("%a %d %b") for d in days],
                       "series": [[lab, var, [round(v) for v in s]] for lab, var, s in series]})
    legend = "".join(f'<span style="--c:var({v})">{_esc(l)}</span>' for l, v, _ in series)
    return f'''<div class="pie-legend">{legend}</div>
<div class="pie-chart" data-line='{_esc(data)}' data-l="{L}" data-r="{R}" data-w="{W}">
<svg viewBox="0 0 {W} {h}" role="img" aria-label="Cumulative P&L by day">
<g class="grid">{g}</g><line class="zero" x1="{L}" x2="{W - R}" y1="{y(0):.1f}" y2="{y(0):.1f}"/>
<g class="axis">{ax}</g>{paths}<line class="xh" y1="{T}" y2="{h - B}" stroke="var(--text-muted)" stroke-dasharray="3 3" opacity="0"/>
<rect class="hit" x="{L}" y="0" width="{W - L - R}" height="{h}" fill="transparent"/></svg><div class="pie-tip"></div></div>'''


def _bar_chart(days, values, h=220):
    W, L, R, T, B = 960, 64, 16, 12, 28
    lo, hi = min(values + [0]), max(values + [0])
    pad = (hi - lo) * 0.08 or 1
    lo, hi = lo - pad, hi + pad
    y = lambda v: T + (h - T - B) * (1 - (v - lo) / (hi - lo))
    n = len(days)
    bw = (W - L - R) / n
    ticks = _ticks(lo, hi)
    g = "".join(f'<line x1="{L}" x2="{W - R}" y1="{y(t):.1f}" y2="{y(t):.1f}"/>' for t in ticks)
    ax = "".join(f'<text x="{L - 8}" y="{y(t) + 4:.1f}" text-anchor="end">{inr(t, compact=True)}</text>' for t in ticks)
    step = max(1, n // 8)
    bars = ""
    for i, (d, v) in enumerate(zip(days, values)):
        x0 = L + i * bw + 1
        w = max(2, bw - 2)
        y0, y1 = sorted((y(0), y(v)))
        hh = max(1, y1 - y0)
        r = min(4, w / 2, hh)
        col = "var(--profit)" if v >= 0 else "var(--loss)"
        # round only the data end
        if v >= 0:
            p = f"M{x0},{y1} V{y0 + r} Q{x0},{y0} {x0 + r},{y0} H{x0 + w - r} Q{x0 + w},{y0} {x0 + w},{y0 + r} V{y1} Z"
        else:
            p = f"M{x0},{y0} V{y1 - r} Q{x0},{y1} {x0 + r},{y1} H{x0 + w - r} Q{x0 + w},{y1} {x0 + w},{y1 - r} V{y0} Z"
        tip = _esc(f"{d.strftime('%a %d %b')}: {inr(v)}")
        bars += f'<g class="bar" data-tip="{tip}"><rect x="{L + i * bw}" y="{T}" width="{bw}" height="{h - T - B}" fill="transparent"/><path d="{p}" fill="{col}"/></g>'
        if i % step == 0:
            ax += f'<text x="{x0 + w / 2:.1f}" y="{h - 6}" text-anchor="middle">{d.strftime("%d %b")}</text>'
    return f'''<div class="pie-legend"><span style="--c:var(--profit)">Profit day (+)</span><span style="--c:var(--loss)">Loss day (−)</span></div>
<div class="pie-chart"><svg viewBox="0 0 {W} {h}" role="img" aria-label="Net P&L per day">
<g class="grid">{g}</g><g class="axis">{ax}</g>{bars}<line class="zero" x1="{L}" x2="{W - R}" y1="{y(0):.1f}" y2="{y(0):.1f}"/></svg><div class="pie-tip"></div></div>'''


def _ticks(lo, hi, n=5):
    import math
    span = hi - lo
    raw = span / n
    mag = 10 ** math.floor(math.log10(raw))
    step = min((m * mag for m in (1, 2, 2.5, 5, 10) if m * mag >= raw), default=raw)
    t = math.ceil(lo / step) * step
    out = []
    while t <= hi:
        out.append(round(t, 6)); t += step
    return out


JS = """
document.querySelectorAll('.pie-chart').forEach(c=>{
  const tip=c.querySelector('.pie-tip'), svg=c.querySelector('svg');
  const show=(html,ev)=>{tip.innerHTML=html;tip.style.opacity=1;const b=c.getBoundingClientRect();
    let x=ev.clientX-b.left+12; if(x+tip.offsetWidth>b.width) x=ev.clientX-b.left-tip.offsetWidth-12;
    tip.style.left=Math.max(0,x)+'px'; tip.style.top=(ev.clientY-b.top-10)+'px';};
  const hide=()=>{tip.style.opacity=0; const xh=c.querySelector('.xh'); if(xh) xh.setAttribute('opacity',0);};
  c.querySelectorAll('.bar').forEach(g=>{g.addEventListener('mousemove',e=>show(g.dataset.tip,e));g.addEventListener('mouseleave',hide);});
  if(c.dataset.line){const d=JSON.parse(c.dataset.line),L=+c.dataset.l,R=+c.dataset.r,W=+c.dataset.w,xh=c.querySelector('.xh');
    const fmt=v=>(v<0?'−':v>0?'+':'')+'₹'+Math.abs(v).toLocaleString('en-IN');
    c.querySelector('.hit').addEventListener('mousemove',e=>{const p=svg.createSVGPoint();p.x=e.clientX;p.y=e.clientY;
      const q=p.matrixTransform(svg.getScreenCTM().inverse());const n=d.days.length;
      const i=Math.max(0,Math.min(n-1,Math.round((q.x-L)/((W-L-R)/Math.max(1,n-1)))));const xx=L+(W-L-R)*i/Math.max(1,n-1);
      xh.setAttribute('x1',xx);xh.setAttribute('x2',xx);xh.setAttribute('opacity',1);
      show('<b>'+d.days[i]+'</b><br>'+d.series.map(s=>'<span style="color:var('+s[1]+')">●</span> '+s[0]+': <b>'+fmt(s[2][i])+'</b>').join('<br>'),e);});
    c.querySelector('.hit').addEventListener('mouseleave',hide);}
});
"""


def render(trades, episodes, findings: list[Finding], rp: ReplayResult, rules: RuleSet, impacts, meta=None) -> str:
    meta = meta or {}
    charges = estimate_charges(trades)
    kept_eps = {id(e) for e, _ in rp.kept}
    kept_charges = estimate_charges([t for e in episodes if id(e) in kept_eps for t in e.orders])
    net = rp.original - charges
    disc_net = rp.disciplined - kept_charges
    days = list(rp.daily_original)
    import itertools
    cum_o = list(itertools.accumulate(rp.daily_original[d] for d in days))
    cum_d = list(itertools.accumulate(rp.daily_disciplined[d] for d in days))
    closed = [e for e in episodes if not e.is_open]
    wins = [e for e in closed if e.pnl > 0]
    orders = Counter(t.date for t in trades)
    d1, d2 = min(t.date for t in trades), max(t.date for t in trades)

    head = f'''<header><div class="pie-eyebrow">PIE Trader · Discipline report</div>
<h1>What cost you money, and the rules that fix it</h1>
<p class="pie-lede">{len(trades):,} orders over {len(orders)} trading days ({d1:%d %b} – {d2:%d %b %Y}).
Each finding below is measured in rupees from your own trades. The rules are then replayed against the same trades.</p></header>'''
    if meta.get("period") and meta.get("period_note"):
        head += f'<p class="pie-muted">{_esc(meta["period_note"])}</p>'

    hero = f'''<section class="pie-card" style="margin-top:24px">
<div class="pie-hero">
 <div><div class="pie-eyebrow">What you made</div><div class="value">{money(net, compact=True)}</div><div class="pie-muted">after ≈{inr(charges, sign=False)} in charges</div></div>
 <div class="pie-arrow" aria-hidden="true">→</div>
 <div><div class="pie-eyebrow">Same trades, PIE rules on</div><div class="value">{money(disc_net, compact=True)}</div><div class="pie-muted">{len(rp.kept)} of {len(closed)} positions allowed</div></div>
 <div style="margin-left:auto"><div class="pie-eyebrow">Losses avoided</div><div class="value">{money(disc_net - net, compact=True)}</div><div class="pie-muted">conservative: stop-losses not simulated</div></div>
</div></section>
<section class="pie-grid" style="margin-top:16px">
 <div class="pie-card pie-stat"><div class="label">Win rate</div><div class="value">{len(wins) / len(closed):.0%}</div><div class="sub">{len(wins)} of {len(closed)} positions</div></div>
 <div class="pie-card pie-stat"><div class="label">Avg win / avg loss</div><div class="value sm num">{inr(sum(e.pnl for e in wins) / max(1, len(wins)), sign=False)} / {inr(sum(e.pnl for e in closed if e.pnl < 0) / max(1, len(closed) - len(wins)), sign=False)}</div><div class="sub">losers are bigger than winners</div></div>
 <div class="pie-card pie-stat"><div class="label">Orders per day</div><div class="value">{len(trades) / len(orders):.0f}</div><div class="sub">peak {max(orders.values())} on {max(orders, key=orders.get):%d %b}</div></div>
 <div class="pie-card pie-stat"><div class="label">Charges as % of gross loss</div><div class="value">{charges / max(1, -rp.original):.0%}</div><div class="sub">{inr(charges, sign=False)} paid to broker and exchange</div></div>
</section>'''

    charts = f'''<h2>Your money, day by day</h2><p class="pie-muted">Gross P&L before charges. Hover for the values.</p>
<div class="pie-card">{_line_chart(days, [("Actual", "--series-2", cum_o), ("With PIE rules", "--series-1", cum_d)])}</div>
<div class="pie-card" style="margin-top:16px"><h3 style="margin-bottom:8px">P&L per day (actual)</h3>{_bar_chart(days, [rp.daily_original[d] for d in days])}</div>'''

    fs = ""
    for f in findings:
        icon, lab = SEV[f.severity]
        rows = ""
        if f.evidence and "symbol" in f.evidence[0]:
            rows = "".join(f'<tr><td>{_esc(e["date"])}</td><td>{_esc(e["symbol"])}</td><td class="r">{e["max_qty"]}</td><td class="r">{e["entries"]}</td><td class="r">{money(e["pnl"])}</td></tr>' for e in f.evidence)
            rows = f'<details><summary>Worst examples</summary><div class="pie-table-wrap"><table class="pie-table"><tr><th>Date</th><th>Contract</th><th class="r">Max qty</th><th class="r">Entries</th><th class="r">P&L</th></tr>{rows}</table></div></details>'
        elif f.evidence and "orders" in f.evidence[0]:
            rows = "".join(f'<tr><td>{_esc(e["date"])}</td><td class="r">{e["orders"]}</td><td class="r">{money(e["pnl"])}</td></tr>' for e in f.evidence)
            rows = f'<details><summary>Worst days</summary><div class="pie-table-wrap"><table class="pie-table"><tr><th>Date</th><th class="r">Orders</th><th class="r">P&L</th></tr>{rows}</table></div></details>'
        fs += f'''<article class="pie-card pie-finding"><header><h3>{_esc(f.title)}</h3>
<span class="pie-badge sev-{f.severity}"><i>{icon}</i>{lab}</span></header>
<p>{_esc(f.summary)}</p><div class="pie-rule"><b>Rule:</b> {_esc(f.fix)}</div>{rows}</article>'''
    find = f'''<h2>The mistakes, ranked</h2><p class="pie-muted">These overlap: one bad trade can be oversized, a revenge trade and on expiry day all at once. So the rupee figures don't add up to the total.</p>
<div style="display:grid;gap:16px">{fs}</div>'''

    imp = "".join(f'<tr><td>{_esc(l)}</td><td class="r">{money(v)}</td></tr>' for l, v in impacts)
    blk = "".join(f'<tr><td>{_esc(k)}</td><td class="r">{len(v)}</td><td class="r">{money(-sum(p for _, p in v))}</td></tr>'
                  for k, v in sorted(rp.blocked.items(), key=lambda kv: sum(p for _, p in kv[1])))
    trim = sum(k - o for _, o, k, _ in rp.trimmed)
    blk += f'<tr><td>Trimmed (size cap / averaging removed)</td><td class="r">{len(rp.trimmed)}</td><td class="r">{money(trim)}</td></tr>'
    rules_html = f'''<h2>The rule set</h2><p class="pie-muted">Edit <code>rules.toml</code> to change these. The Guard checks every new order against them before it is sent.</p>
<div class="pie-rules">
<div>Daily loss limit<b>{inr(-rules.max_daily_loss)}</b></div><div>Weekly loss limit<b>{inr(-rules.max_weekly_loss)}</b></div>
<div>Max loss per trade<b>{inr(-rules.max_loss_per_trade)}</b></div><div>Stop-loss on premium<b>{rules.stop_loss_pct:.0%}</b></div>
<div>Max lots per position<b>{rules.max_lots}</b></div><div>Max trades / orders per day<b>{rules.max_trades_per_day} / {rules.max_orders_per_day}</b></div>
<div>Stop after losses in a row<b>{rules.max_consecutive_losses}</b></div><div>Cooldown after a loss<b>{rules.cooldown_minutes} min</b></div>
<div>Entries on expiry day<b>{"Blocked" if rules.no_expiry_day_entries else "Allowed"}</b></div><div>Averaging down<b>{"Blocked" if rules.no_averaging_down else "Allowed"}</b></div>
<div>Flat by (weekly options)<b>{rules.flat_by}</b></div><div>Min days to expiry to hold overnight<b>{rules.overnight_min_dte}</b></div>
</div>
<div class="pie-grid" style="margin-top:16px;align-items:start">
<div class="pie-card"><h3>What each rule saves on its own</h3><p class="pie-muted">Measured alone. Rules overlap, so these don't add up.</p>
<div class="pie-table-wrap"><table class="pie-table"><tr><th>Rule</th><th class="r">Saved</th></tr>{imp}</table></div></div>
<div class="pie-card"><h3>What the full rule set did</h3><p class="pie-muted">A positive number means the rule kept you out of positions that lost money.</p>
<div class="pie-table-wrap"><table class="pie-table"><tr><th>Reason</th><th class="r">Positions</th><th class="r">Effect</th></tr>{blk}</table></div></div>
</div>'''

    method = f'''<h2>How this is calculated</h2><div class="pie-card pie-muted">
<p>Positions are matched first-in first-out. A position runs from flat back to flat on one contract.
The statement has no timestamps, so the order of trades within a day comes from the exchange trade numbers, which rise through the session. This ordering is approximate.</p>
<p>Charges are estimated at discount-broker rates: ₹20 per order, STT, exchange and SEBI fees, stamp duty and GST.
Your contract notes have the exact figures.</p>
<p>The replay can skip trades, cap size and remove averaging-down adds. It cannot exit earlier at a stop, because that needs tick data.
Stop-losses only shrink losses, so the "PIE rules on" figure is a floor, not a forecast.
{len([e for e in episodes if e.is_open])} position(s) were still open at the end of the file and are left out.</p></div>'''

    css = CSS.read_text() if CSS.exists() else ""
    return f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>PIE Discipline Report</title><style>{css}</style></head>
<body><main class="pie-page">{head}{hero}{charts}{find}{rules_html}{method}</main><script>{JS}</script></body></html>'''
