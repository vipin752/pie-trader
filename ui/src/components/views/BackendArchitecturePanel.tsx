import React, { useState } from 'react';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Database,
  GitBranch,
  Loader2,
  RefreshCw,
  Server,
  ShieldCheck,
} from 'lucide-react';
import {
  BackendConnectionConfig,
  BackendServiceProbeResult,
  IndexSymbol,
} from '../../types/pie';
import { pieApi } from '../../services/pieApiClient';

interface BackendArchitecturePanelProps {
  activeIndex: IndexSymbol;
  backendConfig: BackendConnectionConfig;
  onUpdateBackendConfig: (patch: Partial<BackendConnectionConfig>) => void;
}

const ADR_DECISIONS = [
  {
    component: 'Express Authoritative Desk + React 19 UI',
    decision: 'REMAIN / HARDENED',
    detail: 'Retained as active full-stack preview & desk runtime; financial state migrated out of browser localStorage into server-side multi-user store (.data/pie_desk_store.json).',
  },
  {
    component: 'Agentic GenAI Platform (server/ai/*)',
    decision: 'REFACTORED',
    detail: 'Replaced ad-hoc Gemini calls with provider-independent LlmGateway (timeouts, retries, fallback, token/cost caps), ToolRegistry, PermissionEngine, and Trading vs Product Agent isolation.',
  },
  {
    component: 'backend/pietrader-gateway (Java 21 / Boot 3.4.1)',
    decision: 'MIGRATE → JAVA 25 + PHASE 7 AUTH',
    detail: 'Main gateway (RiskManager, ExitEngine, Angel One WS, Swagger :8080) requires upgrade from Java 21 / Boot 3.4.1 to Java 25 LTS and replacement of Spring Security permitAll() with OAuth2/JWT.',
  },
  {
    component: 'java/pie-risk-engine (Java 25 / Boot 4.1.2)',
    decision: 'TARGET RISK AUTHORITY',
    detail: 'Already aligned on Java 25 LTS, Flyway, Postgres 17, Redis 7 & Actuator. Rollback path preserves gateway RiskManager until Flyway schema parity & Testcontainers pass.',
  },
  {
    component: 'analytics/ FastAPI (:8000) & Option Scraper',
    decision: 'CO-DEPLOYED ANALYTICS ENGINE',
    detail: 'Consumed via /api/backend/sync proxy mapping OptionAnalyticsDTO & MarketContextDTO without introducing premature Kafka/K8s microservices.',
  },
];

const VERIFICATION_ITEMS: {
  id: number;
  label: string;
  status: 'Verified Locally' | 'Simulated / External Pending';
  note: string;
}[] = [
  {
    id: 1,
    label: 'Authoritative Server Persistence',
    status: 'Verified Locally',
    note: 'Capital config, reservations, positions & audit logs persist in server .data/pie_desk_store.json (localStorage holds UI prefs only).',
  },
  {
    id: 2,
    label: 'Multi-User Data Isolation',
    status: 'Verified Locally',
    note: 'HMAC-signed session token isolates user desks by normalized account ID; collective cohorts are anonymized.',
  },
  {
    id: 3,
    label: 'Capital Allocation & Atomic Reservation',
    status: 'Verified Locally',
    note: 'Ring-fenced ₹2L vs ₹8L undeployed guard + idempotent reservations tested via tests/capitalAllocation.test.ts.',
  },
  {
    id: 4,
    label: 'Dynamic Option Lot Sizing',
    status: 'Verified Locally',
    note: 'PositionSizingEngine enforces min(risk, capital, strategy, index, freeze, daily risk) with slippage & statutory taxes.',
  },
  {
    id: 5,
    label: 'Central Risk Enforcement',
    status: 'Verified Locally',
    note: 'Non-bypassable RiskManager vetoes anti-averaging, spread > limit, cooldowns, and Kill Switch.',
  },
  {
    id: 6,
    label: 'Broker Order Lifecycle & Reconciliation',
    status: 'Simulated / External Pending',
    note: 'Partial fills & rejections reconcile locally in Paper Desk; live Angel One WS/order routing awaits Java Gateway connectivity.',
  },
  {
    id: 7,
    label: 'Live Market Data Freshness',
    status: 'Simulated / External Pending',
    note: 'Uses deterministic Black-Scholes & index snapshots when external Gateway (:8080) or Python FastAPI (:8000) is unreachable.',
  },
  {
    id: 8,
    label: 'Agent Permissions & Tool Execution',
    status: 'Verified Locally',
    note: 'PermissionEngine blocks cross-domain violations; every tool execution & token benchmark is persisted in DB.',
  },
  {
    id: 9,
    label: 'Personal & Collective Intelligence Privacy',
    status: 'Verified Locally',
    note: 'get_collective_benchmarks strips private emails/account identifiers before passing cohorts to LLM Gateway.',
  },
  {
    id: 10,
    label: 'Product Improvement Audit History',
    status: 'Verified Locally',
    note: 'ProductFeedback items enforce Under Review → Proposed → Tests Verified → Approved for Release audit trail.',
  },
  {
    id: 11,
    label: 'Security & Regulatory Requirements',
    status: 'Simulated / External Pending',
    note: 'Local session HMAC & SEBI checklist gates active; Java Gateway Spring Security permitAll() (Phase 7) remains pending.',
  },
  {
    id: 12,
    label: 'Build, Automated Tests & Typecheck',
    status: 'Verified Locally',
    note: 'Verified via npm run build, tsc --noEmit, and 7/7 capital allocation unit tests.',
  },
  {
    id: 13,
    label: 'Backup, Recovery & Rollback',
    status: 'Simulated / External Pending',
    note: 'Atomic temp-file rename used locally; Postgres 17 PITR & Kubernetes manifests (deploy/k8s/) await cluster rollout.',
  },
];

export const BackendArchitecturePanel: React.FC<BackendArchitecturePanelProps> = ({
  activeIndex,
  backendConfig,
  onUpdateBackendConfig,
}) => {
  const [probing, setProbing] = useState(false);
  const [probeResults, setProbeResults] = useState<BackendServiceProbeResult[]>([]);
  const [syncing, setSyncing] = useState(false);
  const [syncedDto, setSyncedDto] = useState<{
    marketContext: Record<string, unknown>;
    optionAnalytics: Record<string, unknown>;
    usedExternal: boolean;
    note: string;
  } | null>(null);
  const [errorMsg, setErrorMsg] = useState('');

  const handleProbeAll = async () => {
    setProbing(true);
    setErrorMsg('');
    try {
      const res = await pieApi.probeBackends(backendConfig);
      setProbeResults(res.results);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to probe backend endpoints.');
    } finally {
      setProbing(false);
    }
  };

  const handleSyncContracts = async () => {
    setSyncing(true);
    setErrorMsg('');
    try {
      const res = await pieApi.syncBackendContracts(backendConfig, activeIndex);
      setSyncedDto(res);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Failed to sync DTO contracts.');
    } finally {
      setSyncing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* 1. External Backend Connector (Java Gateway :8080, Risk Engine :8081, Python FastAPI :8000) */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div>
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <Server className="w-4 h-4 text-sky-500" />
              Backend Services Bridge (Java Gateway · Risk Engine · Python Analytics)
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Probes external Java/Python services via server-side proxy (/api/backend/*) to avoid CORS issues and automatically falls back to Express Authoritative Desk when offline.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              disabled={probing}
              onClick={handleProbeAll}
              data-tip="Test live HTTP reachability against Express Desk, Java pietrader-gateway (:8080), Java pie-risk-engine (:8081), and Python FastAPI (:8000)."
              className="min-h-[38px] px-3.5 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            >
              {probing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Activity className="w-3.5 h-3.5" />}
              <span>Probe All Backends</span>
            </button>

            <button
              type="button"
              disabled={syncing}
              onClick={handleSyncContracts}
              data-tip={`Fetch and inspect MarketContextDTO & OptionAnalyticsDTO contract for ${activeIndex}.`}
              className="min-h-[38px] px-3.5 py-1.5 rounded-lg border border-slate-300 dark:border-slate-700 text-xs font-semibold inline-flex items-center gap-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer disabled:opacity-50"
            >
              {syncing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
              <span>Sync {activeIndex} DTOs</span>
            </button>
          </div>
        </div>

        {/* Endpoint Configuration */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
          <div>
            <label className="block text-slate-500 dark:text-slate-400 mb-1">
              Java pietrader-gateway (Swagger :8080)
            </label>
            <input
              type="text"
              value={backendConfig.javaGatewayUrl}
              onChange={(e) => onUpdateBackendConfig({ javaGatewayUrl: e.target.value })}
              className="w-full min-h-[38px] px-3 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono text-slate-900 dark:text-white"
            />
          </div>
          <div>
            <label className="block text-slate-500 dark:text-slate-400 mb-1">
              Java 25 pie-risk-engine (Actuator :8081)
            </label>
            <input
              type="text"
              value={backendConfig.javaRiskEngineUrl}
              onChange={(e) => onUpdateBackendConfig({ javaRiskEngineUrl: e.target.value })}
              className="w-full min-h-[38px] px-3 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono text-slate-900 dark:text-white"
            />
          </div>
          <div>
            <label className="block text-slate-500 dark:text-slate-400 mb-1">
              Python FastAPI Analytics (:8000)
            </label>
            <input
              type="text"
              value={backendConfig.pythonAnalyticsUrl}
              onChange={(e) => onUpdateBackendConfig({ pythonAnalyticsUrl: e.target.value })}
              className="w-full min-h-[38px] px-3 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono text-slate-900 dark:text-white"
            />
          </div>
        </div>

        <label className="inline-flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300 cursor-pointer">
          <input
            type="checkbox"
            checked={backendConfig.preferExternalData}
            onChange={(e) => onUpdateBackendConfig({ preferExternalData: e.target.checked })}
          />
          <span>
            Prefer external Java Gateway DTO endpoints when reachable (otherwise use local Express authoritative runtime)
          </span>
        </label>

        {errorMsg && (
          <div className="p-3 rounded-lg border border-rose-500/40 bg-rose-500/10 text-xs text-rose-600 dark:text-rose-300">
            {errorMsg}
          </div>
        )}

        {/* Probe Results */}
        {probeResults.length > 0 && (
          <div className="divide-y divide-slate-200 dark:divide-slate-800 border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden">
            {probeResults.map((r) => (
              <div key={r.serviceId} className="p-3.5 sm:px-4 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-2 bg-slate-50/60 dark:bg-[#0B1120]/60">
                <div className="space-y-0.5">
                  <div className="font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                    <span>{r.label}</span>
                    <span className="font-mono text-[11px] text-slate-500">{r.url}</span>
                  </div>
                  <div className="text-[11px] text-slate-500 dark:text-slate-400">
                    {r.runtimeStack} · {r.authStatus}
                  </div>
                  <div className="text-[11px] text-slate-600 dark:text-slate-300">{r.detail}</div>
                </div>
                <div className="text-right font-mono shrink-0">
                  <div
                    className={`font-semibold ${
                      r.reachable
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {r.reachable ? `ONLINE (${r.statusCode ?? 200})` : 'OFFLINE · FALLBACK ACTIVE'}
                  </div>
                  <div className="text-[11px] text-slate-500">{r.latencyMs} ms</div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Synced Java DTO Contract Preview */}
        {syncedDto && (
          <div className="p-4 rounded-xl border border-sky-500/30 bg-sky-500/5 space-y-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-sky-600 dark:text-sky-400">
                Synced Java DTO Contract ({syncedDto.usedExternal ? 'External Java Gateway' : 'Local Authoritative Fallback'})
              </span>
              <span className="text-[11px] text-slate-500">{syncedDto.note}</span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 font-mono text-[11px]">
              <pre className="p-3 rounded-lg bg-white dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800 overflow-x-auto">
                {JSON.stringify({ MarketContextDTO: syncedDto.marketContext }, null, 2)}
              </pre>
              <pre className="p-3 rounded-lg bg-white dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800 overflow-x-auto">
                {JSON.stringify({ OptionAnalyticsDTO: syncedDto.optionAnalytics }, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </div>

      {/* 2. Architecture Decision Record (ADR-001) */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div>
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <GitBranch className="w-4 h-4 text-sky-500" />
              ADR-001: Express / Java 21 → Java 25 / Python FastAPI Alignment
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Modular boundaries first; no premature Kafka/K8s orchestration until throughput & latency SLOs require it.
            </p>
          </div>
        </div>
        <div className="divide-y divide-slate-200 dark:divide-slate-800">
          {ADR_DECISIONS.map((row) => (
            <div key={row.component} className="p-4 sm:px-5 flex flex-col sm:flex-row sm:items-start justify-between gap-2 text-xs">
              <div className="space-y-1 max-w-3xl">
                <div className="font-semibold text-slate-900 dark:text-white">{row.component}</div>
                <p className="text-slate-600 dark:text-slate-400 leading-relaxed">{row.detail}</p>
              </div>
              <span className="font-mono font-semibold text-[11px] text-sky-600 dark:text-sky-400 shrink-0">
                {row.decision}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* 3. Section 26.F Honest 13-Point Readiness Audit */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center gap-2">
          <Database className="w-4 h-4 text-emerald-500" />
          <div>
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
              Section 26.F Verification & Honest Readiness Ledger
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Separates verified local capabilities from simulated or external Java/Python infrastructure dependencies.
            </p>
          </div>
        </div>
        <div className="divide-y divide-slate-200 dark:divide-slate-800">
          {VERIFICATION_ITEMS.map((item) => {
            const verified = item.status === 'Verified Locally';
            return (
              <div key={item.id} className="p-3.5 sm:px-5 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
                <div className="space-y-0.5">
                  <div className="font-semibold text-slate-900 dark:text-white flex items-center gap-2">
                    {verified ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                    ) : (
                      <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                    )}
                    <span>
                      {item.id}. {item.label}
                    </span>
                  </div>
                  <p className="text-slate-500 dark:text-slate-400 pl-5">{item.note}</p>
                </div>
                <span
                  className={`font-mono font-semibold text-[11px] shrink-0 ${
                    verified
                      ? 'text-emerald-600 dark:text-emerald-400'
                      : 'text-amber-600 dark:text-amber-400'
                  }`}
                >
                  {item.status}
                </span>
              </div>
            );
          })}
        </div>
        <div className="px-5 py-3 bg-slate-50 dark:bg-[#0B1120] border-t border-slate-200 dark:border-slate-800 text-[11px] text-slate-500 flex items-center gap-2">
          <ShieldCheck className="w-3.5 h-3.5 text-sky-500 shrink-0" />
          <span>
            Financial calculations and authorization decisions never depend on an LLM response.
          </span>
        </div>
      </div>
    </div>
  );
};
