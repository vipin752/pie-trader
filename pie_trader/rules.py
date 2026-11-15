"""Discipline rules: configuration, historical replay, and a live pre-trade guard."""
from __future__ import annotations

import datetime as dt
from collections import defaultdict
from dataclasses import asdict, dataclass, field, fields
from pathlib import Path

from .models import Episode, expiry, is_option, underlying

SESSION_MINUTES = 375  # NSE 09:15–15:30


@dataclass
class RuleSet:
    capital: float = 200_000
    max_daily_loss: float = 4_000          # ~2% of capital
    daily_profit_target: float = 0         # ₹; >0 → stop trading for the day once reached
    stop_after_first_loss: bool = False    # one booked loss → done for the day
    max_weekly_loss: float = 10_000
    max_loss_per_trade: float = 1_500      # enforced live by Guard (needs prices)
    stop_loss_pct: float = 0.25            # of option premium
    max_lots: int = 2
    max_trades_per_day: int = 6
    max_orders_per_day: int = 20
    max_consecutive_losses: int = 2
    cooldown_minutes: int = 15
    no_expiry_day_entries: bool = True
    no_averaging_down: bool = True
    flat_by: str = "15:15"                 # no weekly options overnight
    overnight_min_dte: int = 4
    lot_sizes: dict = field(default_factory=lambda: {
        "NIFTY": 65, "BANKNIFTY": 30, "FINNIFTY": 60, "MIDCPNIFTY": 120, "SENSEX": 20, "BANKEX": 30,
    })

    def lot_size(self, und: str) -> int:
        return self.lot_sizes.get(und, 1)

    @classmethod
    def load(cls, path: str | Path | None) -> "RuleSet":
        if not path:
            return cls()
        import tomllib

        data = tomllib.loads(Path(path).read_text())
        flat = {**data.get("risk", {}), **data.get("behaviour", {}), **data.get("session", {})}
        if "lot_sizes" in data:
            flat["lot_sizes"] = {**cls().lot_sizes, **data["lot_sizes"]}
        known = {f.name for f in fields(cls)}
        unknown = set(flat) - known
        if unknown:
            raise ValueError(f"Unknown rule keys: {sorted(unknown)}")
        return cls(**flat)

    def as_dict(self):
        return asdict(self)


# ───────────────────────── historical replay ─────────────────────────

@dataclass
class ReplayResult:
    original: float
    disciplined: float
    kept: list            # (episode, kept_pnl)
    blocked: dict         # reason -> list[(episode, pnl_avoided)]
    trimmed: list         # (episode, original_pnl, kept_pnl, why)
    daily_original: dict
    daily_disciplined: dict

    @property
    def saved(self):
        return self.disciplined - self.original


def replay(episodes: list[Episode], rules: RuleSet) -> ReplayResult:
    """Re-run history with the rules enforced.

    What we can model from a statement (no tick data): skipping trades the
    rules forbid, capping size, and removing averaging-down adds.
    What we cannot model: an earlier stop-loss exit at a better price.
    Stops only make the result better, so this number is a conservative
    estimate of the savings.
    """
    closed = sorted((e for e in episodes if not e.is_open), key=lambda e: (e.start_date, e.start_seq))
    kept, trimmed = [], []
    blocked: dict = defaultdict(list)
    d_orig: dict = defaultdict(float)
    d_disc: dict = defaultdict(float)
    cool = rules.cooldown_minutes / SESSION_MINUTES

    day = None
    week = None
    week_pnl = 0.0
    for e in closed:
        d_orig[e.end_date] += e.pnl
        if e.start_date != day:
            day, run, n, streak, stopped, last_loss = e.start_date, 0.0, 0, 0, None, {}
        wk = e.start_date.isocalendar()[:2]
        if wk != week:
            week, week_pnl = wk, 0.0

        reason = None
        if stopped:
            reason = stopped
        elif week_pnl <= -rules.max_weekly_loss:
            reason = "Weekly loss limit hit"
        elif rules.no_expiry_day_entries and e.expiry_day and is_option(e.symbol):
            reason = "Expiry-day entry"
        elif n >= rules.max_trades_per_day:
            reason = "Max trades per day"
        elif e.underlying in last_loss and e.start_seq - last_loss[e.underlying] < cool:
            reason = "Cooldown after loss"
        if reason:
            blocked[reason].append((e, e.pnl))
            continue

        # size & averaging
        lot = rules.lot_size(e.underlying)
        cap = rules.max_lots * lot
        idx = [i for i in range(e.entries) if not (rules.no_averaging_down and e.entry_worse[i])]
        pnl = sum(e.entry_pnl[i] for i in idx)
        qty = max((sum(e.entry_qty[i] for i in idx[: k + 1]) for k in range(len(idx))), default=0)
        why = []
        if len(idx) < e.entries:
            why.append("averaging adds removed")
        if qty > cap:
            pnl *= cap / qty
            why.append(f"size capped {qty // lot}→{rules.max_lots} lots")
        if why:
            trimmed.append((e, e.pnl, pnl, ", ".join(why)))

        kept.append((e, pnl))
        d_disc[e.end_date] += pnl
        run += pnl
        week_pnl += pnl
        n += 1
        if pnl < 0:
            streak += 1
            last_loss[e.underlying] = e.end_seq
        else:
            streak = 0
        if run <= -rules.max_daily_loss:
            stopped = "Daily loss limit hit"
        elif rules.stop_after_first_loss and pnl < 0:
            stopped = "First loss booked — done for the day"
        elif rules.daily_profit_target > 0 and run >= rules.daily_profit_target:
            stopped = "Daily profit target reached"
        elif streak >= rules.max_consecutive_losses:
            stopped = "Consecutive-loss stop"

    for d in d_orig:
        d_disc.setdefault(d, 0.0)
    return ReplayResult(
        original=sum(e.pnl for e in closed),
        disciplined=sum(p for _, p in kept),
        kept=kept,
        blocked=dict(blocked),
        trimmed=trimmed,
        daily_original=dict(sorted(d_orig.items())),
        daily_disciplined=dict(sorted(d_disc.items())),
    )


# ───────────────────────── live pre-trade guard ─────────────────────────

@dataclass
class Decision:
    allowed: bool
    reasons: list

    def __bool__(self):
        return self.allowed


class Guard:
    """Stateful pre-trade check. Wire it in front of a broker order API.

        g = Guard(RuleSet.load("rules.toml"))
        d = g.check("NIFTY 17 NOV 2026 25350 CE", "Buy", 130, 63.5, now)
        if d: place_order(...)
        ...
        g.on_close(symbol, realised_pnl, now)
    """

    def __init__(self, rules: RuleSet):
        self.r = rules
        self.day: dt.date | None = None
        self._reset(None)

    def _reset(self, day):
        self.day, self.realised, self.trades, self.orders = day, 0.0, 0, 0
        self.streak, self.losses_today, self.last_loss_at = 0, 0, {}
        self.positions: dict[str, list[float]] = {}  # symbol -> [qty, avg]

    def check(self, symbol: str, side: str, qty: int, price: float, now: dt.datetime,
              risk: float | None = None) -> Decision:
        """risk: ₹ lost if the planned stop is hit. Defaults to stop_loss_pct of premium."""
        if now.date() != self.day:
            self._reset(now.date())
        r, why = self.r, []
        und = underlying(symbol)
        pos = self.positions.get(symbol)
        opening = pos is None or (pos[0] > 0) == (side == "Buy")
        if opening:
            if self.realised <= -r.max_daily_loss:
                why.append(f"Daily loss limit reached (₹{self.realised:,.0f}). Done for today.")
            if r.stop_after_first_loss and self.losses_today >= 1:
                why.append("One loss already booked today. Done for today.")
            if r.daily_profit_target > 0 and self.realised >= r.daily_profit_target:
                why.append(f"Daily target reached (₹{self.realised:,.0f}). Protect it — done for today.")
            if self.streak >= r.max_consecutive_losses:
                why.append(f"{self.streak} losses in a row. Done for today.")
            if pos is None and self.trades >= r.max_trades_per_day:
                why.append(f"Max {r.max_trades_per_day} trades per day reached.")
            if self.orders >= r.max_orders_per_day:
                why.append(f"Max {r.max_orders_per_day} orders per day reached.")
            last = self.last_loss_at.get(und)
            if last and (now - last).total_seconds() < r.cooldown_minutes * 60:
                left = r.cooldown_minutes - int((now - last).total_seconds() // 60)
                why.append(f"Cooldown after a loss on {und}: {left} min left.")
            if r.no_expiry_day_entries and is_option(symbol) and expiry(symbol) == now.date():
                why.append("No new positions in a contract on its expiry day.")
            total = abs(pos[0] if pos else 0) + qty
            cap = r.max_lots * r.lot_size(und)
            if total > cap:
                why.append(f"Size {total} exceeds cap of {cap} ({r.max_lots} lots).")
            if pos and r.no_averaging_down:
                worse = price < pos[1] if side == "Buy" else price > pos[1]
                if worse:
                    why.append("Adding to a losing position (averaging down) is blocked.")
            at_risk = risk if risk is not None else price * qty * r.stop_loss_pct
            if is_option(symbol) and at_risk > r.max_loss_per_trade:
                why.append(f"Risk at the stop is ₹{at_risk:,.0f}, above the per-trade limit of ₹{r.max_loss_per_trade:,.0f}.")
            hh, mm = map(int, r.flat_by.split(":"))
            exp = expiry(symbol)
            if is_option(symbol) and exp and (exp - now.date()).days < r.overnight_min_dte \
                    and now.time() >= dt.time(hh, mm):
                why.append(f"After {r.flat_by}: no new short-dated option positions (they can't be held overnight).")
        return Decision(not why, why)

    def on_fill(self, symbol: str, side: str, qty: int, price: float, now: dt.datetime):
        if now.date() != self.day:
            self._reset(now.date())
        self.orders += 1
        q = qty if side == "Buy" else -qty
        pos = self.positions.get(symbol)
        if pos is None:
            self.positions[symbol] = [q, price]
            self.trades += 1
        elif (pos[0] > 0) == (q > 0):
            pos[1] = (pos[0] * pos[1] + q * price) / (pos[0] + q)
            pos[0] += q
        else:
            pos[0] += q
            if pos[0] == 0:
                del self.positions[symbol]

    def on_close(self, symbol: str, pnl: float, now: dt.datetime):
        if now.date() != self.day:
            self._reset(now.date())
        self.realised += pnl
        if pnl < 0:
            self.streak += 1
            self.losses_today += 1
            self.last_loss_at[underlying(symbol)] = now
        else:
            self.streak = 0


# Values that switch a rule off, for measuring each rule on its own.
_OFF = {
    "max_daily_loss": 1e12, "daily_profit_target": 0.0, "stop_after_first_loss": False, "max_weekly_loss": 1e12, "max_lots": 10**6, "max_trades_per_day": 10**6,
    "max_consecutive_losses": 10**6, "cooldown_minutes": 0, "no_expiry_day_entries": False,
    "no_averaging_down": False,
}
RULE_LABELS = {
    "stop_after_first_loss": "Stop after the first loss",
    "daily_profit_target": "Stop at the daily profit target",
    "no_expiry_day_entries": "No entries on expiry day",
    "max_lots": "Max lots per position",
    "no_averaging_down": "No averaging down",
    "max_daily_loss": "Daily loss limit",
    "max_weekly_loss": "Weekly loss limit",
    "max_consecutive_losses": "Stop after consecutive losses",
    "max_trades_per_day": "Max trades per day",
    "cooldown_minutes": "Cooldown after a loss",
}


def rule_impact(episodes, rules: RuleSet) -> list[tuple[str, float]]:
    """Savings from each replayable rule applied alone (rules interact, so these don't add up)."""
    from dataclasses import replace

    base = replace(rules, **_OFF)
    orig = replay(episodes, base).disciplined
    out = []
    for key, label in RULE_LABELS.items():
        r = replace(base, **{key: getattr(rules, key)})
        out.append((label, replay(episodes, r).disciplined - orig))
    return sorted(out, key=lambda x: -x[1])
