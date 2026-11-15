"""Portfolio-level exposure control across indices (spec §1, §5).

Each index runs its own independent Engine (own Structure, SMC, Direction,
Guard, config). This module is the layer ABOVE that: it decides whether a
candidate entry from any one index's Engine is actually allowed to open,
given what else is already open across the whole portfolio.

Default behaviour matches the spec literally: at most one position open
across the whole portfolio, and never two positions in the same correlation
group even if that cap is raised. This is deliberately conservative —
raise `max_concurrent` only with evidence that the indices you're opening
together actually decouple.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .instruments import CORRELATION_GROUPS


@dataclass
class Decision:
    allowed: bool
    reasons: list = field(default_factory=list)

    def __bool__(self):
        return self.allowed


@dataclass
class PortfolioGuard:
    max_concurrent: int = 1
    allow_same_group: bool = False
    groups: dict = field(default_factory=lambda: dict(CORRELATION_GROUPS))
    open_positions: dict = field(default_factory=dict)   # index name -> group

    def check(self, index: str) -> Decision:
        why = []
        if len(self.open_positions) >= self.max_concurrent:
            why.append(f"portfolio already has {len(self.open_positions)} open position(s) "
                      f"(max {self.max_concurrent}): {sorted(self.open_positions)}")
        group = self.groups.get(index, index)
        if not self.allow_same_group:
            same = [idx for idx, g in self.open_positions.items() if g == group]
            if same:
                why.append(f"{index} is correlated with already-open {same} ({group}) — blocked")
        return Decision(not why, why)

    def register_open(self, index: str):
        self.open_positions[index] = self.groups.get(index, index)

    def register_close(self, index: str):
        self.open_positions.pop(index, None)


@dataclass
class Candidate:
    """One index's best available setup at a given moment, for ranking
    when more than one index wants to trade at once (spec §5: "compare the
    five indices and identify the best validated trading opportunity")."""
    index: str
    direction: str
    setup: str
    rr_to_target: float      # distance to target ÷ stop distance
    mtf_agreement: int       # how many of D/W/M agree with the direction (0-3)

    @property
    def score(self) -> float:
        return self.rr_to_target + self.mtf_agreement


def rank(candidates: list) -> list:
    """Best opportunity first. Ties keep input order (stable sort)."""
    return sorted(candidates, key=lambda c: -c.score)
