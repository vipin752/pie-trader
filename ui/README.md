# PIE Trader — Frontend

React 19 + Vite + TypeScript, with a small Express server (`server.ts`) that
acts as a local "authoritative desk": it serves auth, capital/risk state and
paper-trade simulation from a JSON file store (`.data/`, gitignored) so the
UI is fully usable standalone, before — or without — the real backends
running.

Visual theme is shared with the rest of PIE Trader: `src/index.css`'s
`--profit`/`--loss`/`--surface-*`/etc. variables mirror
[`design/tokens.json`](../design/tokens.json) and
[`design/pie.css`](../design/pie.css) at the repo root exactly.

## Backend contract

`server/connectors/backendBridge.ts` optionally syncs live market data from
the real backends when `preferExternalData` is on and they're reachable:

- `backend/pietrader-gateway`'s `GET /api/trade-card?symbol=NIFTY` (see
  `com.pietrader.mapper.TradeCardMapper`) for NIFTY/BANKNIFTY/FINNIFTY/
  MIDCPNIFTY — SENSEX isn't wired on the gateway yet, so it always uses
  local data for now.
- `java/pie-risk-engine` and `analytics/` (Python) are probed for health but
  not yet synced for live data — see `probeBackendServices`.

When a backend is unreachable, or doesn't cover a field yet (e.g. India VIX,
max pain, expiries — not in `MarketContextDTO`/`/api/trade-card` today), the
bridge falls through cleanly to the local `src/data/pieMarketData.ts`
fixtures rather than erroring. `src/types/pie.ts`'s `BackendServiceProbeResult`
is what the Brokers/Settings view's backend panel shows the user.

## Run locally

```bash
npm install
npm run dev     # tsx server.ts, Vite dev server behind it
```

`GEMINI_API_KEY` (see `.env.example`) is only needed for the AI Hub / agent
features in `server/ai/`; the rest of the app works without it.

## Known gaps before this talks to the real trading system

- No real authentication exists yet on `backend/pietrader-gateway`
  (`SecurityConfig` permits all requests) — this app's own `server/auth/`
  is a separate, UI-side session layer, not a substitute for that.
- "Executing" a trade or basket leg here writes to this app's own
  `.data/pie_desk_store.json` paper-trade simulator — it does **not** go
  through the real `RiskManager`/`OrderManager` pipeline in
  `backend/pietrader-gateway`. Treat it as a UI/UX rehearsal layer until
  that wiring is built, not as the system of record.
