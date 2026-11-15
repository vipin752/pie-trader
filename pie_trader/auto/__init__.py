"""Automated, session-based option trading on market structure.

Pipeline per closed candle of the underlying (NIFTY/SENSEX/BANKNIFTY):

    candles ─► structure (swings, BOS, CHoCH, liquidity pools, sweeps)
            ─► session filter (only trade the good windows)
            ─► setup  (liquidity sweep → BOS the other way, or trend BOS)
            ─► option selection (ATM/ITM, an expiry that outlives the max hold)
            ─► risk sizing (lots from ₹ risk ÷ premium stop) ─► Guard
            ─► broker (paper by default)
            ─► management: ride with structure trailing, exit on CHoCH,
               liquidity target, time stop (max 3 sessions), daily stop.
"""
