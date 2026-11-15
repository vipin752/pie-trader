"""Angel One SmartAPI adapter — SCAFFOLD, NOT YET VALIDATED AGAINST THE LIVE API.

Spec phase 6: "Angel One SmartAPI integration, subject to API validation."
This file gives the adapter its final shape so the engine's Broker protocol is
satisfied; every endpoint call must be verified against the current SmartAPI
docs (https://smartapi.angelbroking.com/docs) before any live order.

Credentials come ONLY from environment variables — never from the repo:
    ANGEL_API_KEY, ANGEL_CLIENT_ID, ANGEL_PIN, ANGEL_TOTP_SECRET

Usage:
    broker = AngelOneBroker(live=False)   # refuses real orders unless live=True
"""
from __future__ import annotations

import datetime as dt
import os

from ..broker import Fill, LiveBrokerNotConfigured

BASE = "https://apiconnect.angelbroking.com"


class AngelOneBroker:
    def __init__(self, live: bool = False, session=None):
        if not live:
            raise LiveBrokerNotConfigured(
                "AngelOneBroker is a live adapter. Use PaperBroker for paper trading; "
                "construct with live=True only after API validation."
            )
        missing = [k for k in ("ANGEL_API_KEY", "ANGEL_CLIENT_ID", "ANGEL_PIN", "ANGEL_TOTP_SECRET")
                   if not os.environ.get(k)]
        if missing:
            raise LiveBrokerNotConfigured(f"Missing environment variables: {missing}")
        self._session = session  # injected requests.Session for tests
        self._jwt = None
        raise NotImplementedError(
            "SmartAPI calls are scaffolded but not validated. Validation checklist: "
            "1) loginByPassword + TOTP → jwtToken; 2) searchScrip → symboltoken for the "
            "option contract; 3) LTP quote; 4) placeOrder (VARIETY=NORMAL, "
            "PRODUCTTYPE=INTRADAY/CARRYFORWARD, DURATION=DAY); 5) modifyOrder for "
            "stop updates with acknowledgement capture; 6) orderBook/tradeBook "
            "reconciliation; 7) emergency square-off via positions + market orders."
        )

    # Broker protocol — shaped, pending validation
    def quote(self, symbol: str) -> float: ...
    def place_market(self, symbol: str, side: str, qty: int, ts: dt.datetime) -> Fill: ...
    def fills(self) -> list: ...
