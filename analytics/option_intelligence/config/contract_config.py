"""
Contract configuration for Indian index options.

Centralized configuration used by all option analytics engines.

Parameters:
- lot_size   : contract multiplier
- tick_size  : minimum price movement
- strike_gap : standard strike spacing
- index_name : display name
"""

from __future__ import annotations


# ---------------------------------------------------------------------
# CONTRACT DEFINITIONS
# ---------------------------------------------------------------------

CONTRACT_CONFIG = {

    "NIFTY": {
        "lot_size": 65,
        "tick_size": 0.05,
        "strike_gap": 50,
        "index_name": "NIFTY 50",
    },

    "BANKNIFTY": {
        "lot_size": 30,
        "tick_size": 0.05,
        "strike_gap": 100,
        "index_name": "NIFTY BANK",
    },

    "FINNIFTY": {
        "lot_size": 40,
        "tick_size": 0.05,
        "strike_gap": 50,
        "index_name": "NIFTY FIN SERVICE",
    },

    "MIDCPNIFTY": {
        "lot_size": 75,
        "tick_size": 0.05,
        "strike_gap": 25,
        "index_name": "NIFTY MIDCAP SELECT",
    },
}


# ---------------------------------------------------------------------
# INTERNAL UTILITIES
# ---------------------------------------------------------------------

def _normalize(symbol: str) -> str:
    """Normalize symbol format."""
    return symbol.upper().strip()


# ---------------------------------------------------------------------
# MAIN ACCESS FUNCTION
# ---------------------------------------------------------------------

def get_contract(symbol: str) -> dict:
    """
    Return full contract configuration.

    Example:
        contract = get_contract("NIFTY")
        lot_size = contract["lot_size"]
    """

    symbol = _normalize(symbol)

    if symbol not in CONTRACT_CONFIG:
        raise ValueError(f"Unsupported symbol: {symbol}")

    return CONTRACT_CONFIG[symbol]


# ---------------------------------------------------------------------
# HELPER FUNCTIONS
# ---------------------------------------------------------------------

def get_lot_size(symbol: str) -> int:
    """Return contract lot size."""
    return get_contract(symbol)["lot_size"]


def get_tick_size(symbol: str) -> float:
    """Return tick size."""
    return get_contract(symbol)["tick_size"]


def get_strike_gap(symbol: str) -> int:
    """Return standard strike gap."""
    return get_contract(symbol)["strike_gap"]


def get_index_name(symbol: str) -> str:
    """Return full index display name."""
    return get_contract(symbol)["index_name"]


# ---------------------------------------------------------------------
# OPTIONAL VALIDATION
# ---------------------------------------------------------------------

def is_valid_symbol(symbol: str) -> bool:
    """Check if symbol is supported."""
    return _normalize(symbol) in CONTRACT_CONFIG


def list_supported_symbols() -> list:
    """Return list of supported symbols."""
    return list(CONTRACT_CONFIG.keys())
    