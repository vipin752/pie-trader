# PIE design system

The visual language for everything PIE Trader shows a trader: reports, the guard UI and the dashboard.
The source of truth is `tokens.json`. `pie.css` implements it.

## Principles
1. **Truth first.** P&L is always net of charges, never rounded in the trader's favour.
2. **The rule is the hero.** Every problem shown is paired with the rule that prevents it, in the accent-bordered `.pie-rule` block.
3. **Calm, not casino.** No flashing, no confetti, no streak celebrations. Losses are clear without shouting.
4. **Sign + word + colour.** Money always carries a true minus (−) or a plus (+). Colour is never the only signal.

## Colour
| Role | Light | Dark | Use |
|---|---|---|---|
| profit | `#2a78d6` blue | `#3987e5` | positive money, profit bars |
| loss | `#e34948` red | `#e66767` | negative money, loss bars |
| accent | `#2a78d6` | `#3987e5` | rules, links, focus |
| series-1 / series-2 | blue / orange | blue / orange | "with rules" vs "actual" |
| status | good `#0ca30c` · warning `#fab219` · serious `#ec835a` · critical `#d03b3b` | same | severity only, always with an icon and a label |

Profit is **blue, not green**. Red–green is the most common colour-blind confusion, and blue–red stays distinct for everyone.
Text uses the text tokens (`.pos`/`.neg` use the darker `-ink` steps for contrast), never the raw series colour.
Dark mode is a separately chosen set of values, not an automatic inversion.

## Type and numbers
- Inter / system sans. JetBrains Mono for code.
- Scale: 12 · 13 · 15 · 18 · 24 · 32 · 48 (hero).
- All money is `tabular-nums` with Indian grouping (`₹1,68,540`). Compact above ₹10k: `₹12.5k`, `₹1.27L`.

## Components
| Class | Purpose |
|---|---|
| `.pie-card` | surface container, 12px radius, 1px border |
| `.pie-hero` | "actual → with rules → avoided" headline numbers |
| `.pie-stat` | KPI tile: label, value, sub |
| `.pie-badge.sev-*` | severity: icon + label + status colour |
| `.pie-finding` | mistake card: title, badge, summary, rule, evidence |
| `.pie-rule` | the fix. Accent left border |
| `.pie-rules` | key/value grid of the active rule set |
| `.pie-table` | dense numeric table, right-aligned numbers |
| `.pie-chart` | inline SVG chart + `.pie-tip` hover tooltip |

## Charts
2px lines and 4px rounded data-ends anchored to zero. The grid sits back in the border colour. Every chart has a legend and a hover tooltip.
One y-axis per chart, never two. Bars use profit or loss colour by sign.
On phones, charts keep a minimum width of 640px and scroll sideways instead of shrinking until they can't be read.
