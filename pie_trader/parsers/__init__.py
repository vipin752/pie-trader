from __future__ import annotations

from pathlib import Path

from .generic_csv import load_csv
from .mirae import load_mirae_xlsx


def load_trades(path: str | Path):
    p = Path(path)
    if p.suffix.lower() in (".xlsx", ".xlsm"):
        return load_mirae_xlsx(p)
    if p.suffix.lower() == ".csv":
        return load_csv(p)
    raise ValueError(f"Unsupported trade file: {p.name}")


__all__ = ["load_trades", "load_csv", "load_mirae_xlsx"]
