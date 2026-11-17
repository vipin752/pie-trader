import {
  BackendConnectionConfig,
  BackendServiceProbeResult,
  IndexSymbol,
} from '../../src/types/pie';
import { INDICES_MASTER } from '../../src/data/pieMarketData';

/**
 * Mirrors Java Gateway DTOs:
 *   com.pietrader.dto.MarketContextDTO
 *   com.pietrader.dto.OptionAnalyticsDTO
 */
export interface JavaMarketContextDto {
  symbol: string;
  spotPrice: number;
  indiaVix: number;
  pcr: number;
  regime: string;
  source: 'java-gateway' | 'local-express-fallback';
}

export interface JavaOptionAnalyticsDto {
  symbol: string;
  maxPain: number;
  lotSize: number;
  strikeInterval: number;
  atmStrike: number;
  expiries: string[];
  source: 'java-gateway' | 'python-analytics' | 'local-express-fallback';
}

async function probeHttp(
  url: string,
  timeoutMs = 2200,
  accessToken?: string | null
): Promise<{ ok: boolean; statusCode?: number; latencyMs: number; bodyText?: string; error?: string }> {
  const start = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const resp = await fetch(url, {
      signal: controller.signal,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : undefined,
    });
    const text = await resp.text();
    return {
      ok: resp.ok,
      statusCode: resp.status,
      latencyMs: Date.now() - start,
      bodyText: text.slice(0, 400),
    };
  } catch (err) {
    return {
      ok: false,
      latencyMs: Date.now() - start,
      error: err instanceof Error ? err.message : 'Connection refused',
    };
  } finally {
    clearTimeout(timer);
  }
}

export async function probeBackendServices(
  config: BackendConnectionConfig,
  gatewayAccessToken?: string | null
): Promise<BackendServiceProbeResult[]> {
  const now = new Date().toISOString();

  const [gatewayRes, riskRes, pythonRes] = await Promise.all([
    // /actuator/health itself is public (see backend/pietrader-gateway's
    // SecurityConfig) — no token needed for this specific probe — but
    // /api-docs (the fallback probe) is now gated behind PERM_trader.ops,
    // so it's attempted with whatever gateway token this user has.
    probeHttp(`${config.javaGatewayUrl.replace(/\/$/, '')}/actuator/health`).then(async (r) => {
      if (r.ok) return r;
      return probeHttp(`${config.javaGatewayUrl.replace(/\/$/, '')}/api-docs`, 2200, gatewayAccessToken);
    }),
    probeHttp(`${config.javaRiskEngineUrl.replace(/\/$/, '')}/actuator/health`),
    probeHttp(`${config.pythonAnalyticsUrl.replace(/\/$/, '')}/health`).then(async (r) => {
      if (r.ok) return r;
      return probeHttp(`${config.pythonAnalyticsUrl.replace(/\/$/, '')}/docs`);
    }),
  ]);

  return [
    {
      serviceId: 'express-desk',
      label: 'Express Authoritative Desk (Active Sandbox Runtime)',
      url: '/api/*',
      reachable: true,
      statusCode: 200,
      latencyMs: 1,
      runtimeStack: 'Node 22 + Express + TypeScript + Authoritative File Store',
      authStatus: 'HMAC Session Token + Multi-User Tenant Isolation Active',
      contractVersion: 'v1.0 (Local Authoritative)',
      detail: 'Serving authoritative capital, reservations, RiskManager checks, Agentic AI Gateway & telemetry.',
      checkedAt: now,
    },
    {
      serviceId: 'java-gateway',
      label: 'backend/pietrader-gateway',
      url: config.javaGatewayUrl,
      reachable: gatewayRes.ok,
      statusCode: gatewayRes.statusCode,
      latencyMs: gatewayRes.latencyMs,
      runtimeStack: 'Java 21 · Spring Boot 3.4.1 (Upgrade to Java 25 LTS pending)',
      authStatus: gatewayAccessToken
        ? 'Keycloak JWT resource server — forwarding this user\'s access token'
        : 'Keycloak JWT resource server — no gateway token for this user (sign in via the OIDC/Keycloak provider to get one)',
      contractVersion: 'Swagger /swagger-ui.html · com.pietrader.dto.*',
      detail: gatewayRes.ok
        ? `Connected (${gatewayRes.statusCode}) — OptionController, DashboardController, ExecutionController ready.`
        : `Unreachable from sandbox (${gatewayRes.error ?? 'timeout'}). Local Express Desk fallback active.`,
      checkedAt: now,
    },
    {
      serviceId: 'java-risk-engine',
      label: 'java/pie-risk-engine',
      url: config.javaRiskEngineUrl,
      reachable: riskRes.ok,
      statusCode: riskRes.statusCode,
      latencyMs: riskRes.latencyMs,
      runtimeStack: 'Java 25 (LTS) · Spring Boot 4.1.2 · Flyway · Postgres 17 + Redis 7',
      authStatus: 'Internal Risk Engine Profile',
      contractVersion: 'Actuator + Prometheus Micrometer',
      detail: riskRes.ok
        ? `Connected (${riskRes.statusCode}) — Central RiskManager & Postgres 17 persistence active.`
        : `Unreachable from sandbox (${riskRes.error ?? 'timeout'}). Local PositionSizing & RiskManager active.`,
      checkedAt: now,
    },
    {
      serviceId: 'python-analytics',
      label: 'analytics/ (FastAPI + NSE Scraper)',
      url: config.pythonAnalyticsUrl,
      reachable: pythonRes.ok,
      statusCode: pythonRes.statusCode,
      latencyMs: pythonRes.latencyMs,
      runtimeStack: 'Python 3.11+ · FastAPI · Uvicorn · openpyxl',
      authStatus: 'Unauthenticated internal REST (Port 8000)',
      contractVersion: 'Option Intelligence FastAPI',
      detail: pythonRes.ok
        ? `Connected (${pythonRes.statusCode}) — Python analytics & option-chain scraper online.`
        : `Unreachable from sandbox (${pythonRes.error ?? 'timeout'}). Local Black-Scholes option chain active.`,
      checkedAt: now,
    },
  ];
}

// backend/pietrader-gateway's DashboardController (the real, existing
// contract — see com.pietrader.mapper.TradeCardMapper) only knows these 4
// index symbols today; SENSEX isn't wired on that side yet, so it always
// uses local data until the gateway adds it (see Trade Card UI Spec decisions).
function toGatewaySymbol(indexSymbol: IndexSymbol): string | null {
  const map: Record<IndexSymbol, string | null> = {
    'NIFTY 50': 'NIFTY',
    BANKNIFTY: 'BANKNIFTY',
    FINNIFTY: 'FINNIFTY',
    MIDCPNIFTY: 'MIDCPNIFTY',
    SENSEX: null,
  };
  return map[indexSymbol];
}

export async function fetchSyncedMarketContracts(
  config: BackendConnectionConfig,
  indexSymbol: IndexSymbol,
  gatewayAccessToken?: string | null
): Promise<{
  marketContext: JavaMarketContextDto;
  optionAnalytics: JavaOptionAnalyticsDto;
  usedExternal: boolean;
  note: string;
}> {
  const localSpec = INDICES_MASTER[indexSymbol];
  const atm = Math.round(localSpec.spot / localSpec.strikeInterval) * localSpec.strikeInterval;
  const gatewaySymbol = toGatewaySymbol(indexSymbol);

  if (config.preferExternalData && gatewaySymbol) {
    const gwBase = config.javaGatewayUrl.replace(/\/$/, '');
    // The real, existing contract: GET /api/trade-card?symbol=... on
    // DashboardController, mapped by TradeCardMapper.toTradeCard() — not an
    // invented /api/dashboard/context endpoint, which does not exist.
    // /api/trade-card requires PERM_trader.read on the gateway now — see
    // backend/pietrader-gateway's SecurityConfig. With no token (or an
    // unauthenticated/under-permissioned one) this 401s/403s, gwAttempt.ok
    // is false, and the local-fallback branch below runs exactly as it
    // already does when the gateway is simply unreachable.
    const gwAttempt = await probeHttp(
      `${gwBase}/api/trade-card?symbol=${encodeURIComponent(gatewaySymbol)}`,
      2000,
      gatewayAccessToken
    );
    if (gwAttempt.ok && gwAttempt.bodyText) {
      try {
        const card = JSON.parse(gwAttempt.bodyText) as Record<string, unknown>;
        if (card.status === 'OK' && typeof card.spot === 'number') {
          const option = (card.option as Record<string, unknown>) ?? {};
          const risk = (card.risk as Record<string, unknown>) ?? {};
          return {
            marketContext: {
              symbol: (card.symbol as string) ?? indexSymbol,
              spotPrice: card.spot as number,
              // indiaVix has no real-DTO source yet (MarketContextDTO carries
              // symbol/spot/atm/contract/expiry/session only) — local until
              // the gateway adds one; see Trade Card UI Spec open decisions.
              indiaVix: localSpec.indiaVix,
              pcr: typeof option.pcr === 'number' ? option.pcr : localSpec.pcr,
              regime: (card.regime as string) ?? localSpec.regime,
              source: 'java-gateway',
            },
            optionAnalytics: {
              symbol: (card.symbol as string) ?? indexSymbol,
              maxPain: localSpec.maxPain, // not in /api/trade-card today
              lotSize: localSpec.lotSize,
              strikeInterval: localSpec.strikeInterval,
              atmStrike: typeof card.atm === 'number' ? (card.atm as number) : atm,
              expiries: localSpec.expiries, // not in /api/trade-card today
              source: 'java-gateway',
            },
            usedExternal: true,
            note: `Synced live trade card (${option.selected ?? 'no strike yet'}, RR ${
              risk.risk_reward_ratio ?? '—'
            }) from ${gwBase}/api/trade-card.`,
          };
        }
      } catch {
        // Fall through cleanly if non-JSON
      }
    }
  }

  return {
    marketContext: {
      symbol: localSpec.symbol,
      spotPrice: localSpec.spot,
      indiaVix: localSpec.indiaVix,
      pcr: localSpec.pcr,
      regime: localSpec.regime,
      source: 'local-express-fallback',
    },
    optionAnalytics: {
      symbol: localSpec.symbol,
      maxPain: localSpec.maxPain,
      lotSize: localSpec.lotSize,
      strikeInterval: localSpec.strikeInterval,
      atmStrike: atm,
      expiries: localSpec.expiries,
      source: 'local-express-fallback',
    },
    usedExternal: false,
    note: !gatewaySymbol
      ? `${indexSymbol} isn't wired on backend/pietrader-gateway yet (DashboardController only knows NIFTY/BANKNIFTY/FINNIFTY/MIDCPNIFTY) — using local data.`
      : config.preferExternalData
        ? 'External Java Gateway / Python FastAPI unreachable from sandbox; mapped local authoritative DTOs cleanly.'
        : 'Mapped local MarketContextDTO & OptionAnalyticsDTO contract.',
  };
}
