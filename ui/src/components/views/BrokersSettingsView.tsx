import React, { useState } from 'react';
import { Link2, ShieldCheck, Lock, FileText } from 'lucide-react';
import {
  AuditLogEntry,
  BackendConnectionConfig,
  BrokerConnection,
  BrokersInsideTab,
  IndexSymbol,
  UserSession,
} from '../../types/pie';
import { BackendArchitecturePanel } from './BackendArchitecturePanel';

interface BrokersSettingsViewProps {
  insideTab: BrokersInsideTab;
  brokers: BrokerConnection[];
  onToggleBrokerConnection: (id: BrokerConnection['id']) => void;
  onToggleBrokerPermission: (
    id: BrokerConnection['id'],
    perm: 'marketDataPerm' | 'portfolioPerm' | 'orderPlacementPerm'
  ) => void;
  auditLogs: AuditLogEntry[];
  session: UserSession;
  isDark: boolean;
  onToggleTheme: () => void;
  activeIndex: IndexSymbol;
  backendConfig: BackendConnectionConfig;
  onUpdateBackendConfig: (patch: Partial<BackendConnectionConfig>) => void;
}

export const BrokersSettingsView: React.FC<BrokersSettingsViewProps> = ({
  insideTab,
  brokers,
  onToggleBrokerConnection,
  onToggleBrokerPermission,
  auditLogs,
  session,
  isDark,
  onToggleTheme,
  activeIndex,
  backendConfig,
  onUpdateBackendConfig,
}) => {
  const [sebiConsentChecked, setSebiConsentChecked] = useState(false);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-xl font-bold text-slate-900 dark:text-white">
          Broker Adapters, Compliance & Audit Trail
        </h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
          Market data access and trading authorization remain strictly separated. Live order execution is disabled by default.
        </p>
      </div>

      {/* Inside Tab 1: Broker Adapters */}
      {insideTab === 'adapters' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] divide-y divide-slate-200 dark:divide-slate-800">
            {brokers.map((b) => (
              <div key={b.id} className="p-4 sm:p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                    <span>Priority #{b.priority}</span>
                    <span aria-hidden="true">·</span>
                    <span
                      className={`font-semibold ${
                        b.authStatus === 'Connected'
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : b.authStatus === 'Token Expired'
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-slate-500'
                      }`}
                    >
                      {b.authStatus}
                    </span>
                    <span aria-hidden="true">·</span>
                    <span className="font-mono">Heartbeat: {b.lastHeartbeat}</span>
                  </div>
                  <div className="font-display font-bold text-base text-slate-900 dark:text-white flex items-center gap-2">
                    <Link2 className="w-4 h-4 text-sky-500" />
                    <span>{b.name}</span>
                  </div>
                  <div className="text-xs text-slate-500 dark:text-slate-400 font-mono">
                    2FA: {b.twoFactorVerified ? 'Verified' : 'Pending'} · Static IP: {b.staticIpVerified ? 'Verified' : 'Unverified'} · API Headroom: {b.rateLimitHeadroomPct}%
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2.5 text-xs">
                  <button
                    type="button"
                    disabled={b.authStatus !== 'Connected'}
                    onClick={() => onToggleBrokerPermission(b.id, 'marketDataPerm')}
                    data-tip="Allow live market feed & option chain streaming from this broker adapter."
                    className={`min-h-[36px] px-3 py-1.5 rounded-lg border font-medium cursor-pointer disabled:opacity-40 ${
                      b.marketDataPerm
                        ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                        : 'border-slate-200 dark:border-slate-700 text-slate-500'
                    }`}
                  >
                    Data: {b.marketDataPerm ? 'On' : 'Off'}
                  </button>
                  <button
                    type="button"
                    disabled={b.authStatus !== 'Connected'}
                    onClick={() => onToggleBrokerPermission(b.id, 'portfolioPerm')}
                    data-tip="Allow read-only sync of margins, holdings, and open positions."
                    className={`min-h-[36px] px-3 py-1.5 rounded-lg border font-medium cursor-pointer disabled:opacity-40 ${
                      b.portfolioPerm
                        ? 'border-sky-500/40 bg-sky-500/10 text-sky-600 dark:text-sky-400'
                        : 'border-slate-200 dark:border-slate-700 text-slate-500'
                    }`}
                  >
                    Portfolio: {b.portfolioPerm ? 'On' : 'Off'}
                  </button>
                  <button
                    type="button"
                    disabled={b.authStatus !== 'Connected' || !sebiConsentChecked}
                    onClick={() => onToggleBrokerPermission(b.id, 'orderPlacementPerm')}
                    data-tip={
                      !sebiConsentChecked
                        ? 'Locked: Complete the SEBI Compliance Checklist inside Compliance & Audit first.'
                        : 'Authorize live order placement permission for this 2FA-verified broker.'
                    }
                    className={`min-h-[36px] px-3 py-1.5 rounded-lg border font-medium cursor-pointer disabled:opacity-40 ${
                      b.orderPlacementPerm
                        ? 'border-amber-500/50 bg-amber-500/10 text-amber-600 dark:text-amber-300'
                        : 'border-slate-200 dark:border-slate-700 text-slate-500'
                    }`}
                  >
                    Order Auth: {b.orderPlacementPerm ? 'Authorized' : 'Locked'}
                  </button>

                  <button
                    type="button"
                    onClick={() => onToggleBrokerConnection(b.id)}
                    data-tip={
                      b.authStatus === 'Connected'
                        ? `Disconnect ${b.name} API session.`
                        : `Authenticate ${b.name} with read-only data scope first.`
                    }
                    className="min-h-[36px] px-3.5 py-1.5 rounded-lg bg-slate-900 dark:bg-slate-800 text-white font-semibold hover:opacity-90 cursor-pointer"
                  >
                    {b.authStatus === 'Connected' ? 'Disconnect' : 'Authenticate'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Inside Tab 2: Compliance & Audit */}
      {insideTab === 'compliance' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-500" />
              SEBI & Exchange Retail API Checklist
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Connecting a read-only broker session never authorizes automated order placement. Confirm regulatory compliance before enabling manual live order routing.
            </p>
            <label className="flex items-start gap-2.5 text-xs text-slate-700 dark:text-slate-300 p-3 rounded-lg border border-slate-200 dark:border-slate-800 cursor-pointer">
              <input
                type="checkbox"
                checked={sebiConsentChecked}
                onChange={(e) => setSebiConsentChecked(e.target.checked)}
                className="mt-0.5"
              />
              <span>
                I acknowledge explicit 2FA broker consent, static IP tagging, idempotent order keys, and exchange order-per-second rate limits.
              </span>
            </label>
            <div className="text-xs text-slate-500 flex items-center gap-2">
              <Lock className="w-3.5 h-3.5 text-sky-500" />
              <span>TradingOrchestrator remains the sole execution authority.</span>
            </div>
          </div>

          <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 flex items-center gap-2">
              <FileText className="w-4 h-4 text-sky-500" />
              <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
                Immutable Decision & Execution Audit Trail
              </h3>
            </div>
            <div className="divide-y divide-slate-200 dark:divide-slate-800 max-h-96 overflow-y-auto">
              {auditLogs.map((log) => (
                <div key={log.id} className="p-3.5 sm:px-5 text-xs space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-semibold text-slate-700 dark:text-slate-200">
                      {log.actor} · {log.action}
                    </span>
                    <span
                      className={`font-mono font-semibold ${
                        log.outcome === 'APPROVED'
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : log.outcome === 'REJECTED'
                          ? 'text-rose-600 dark:text-rose-400'
                          : 'text-sky-600 dark:text-sky-400'
                      }`}
                    >
                      {log.outcome} · {log.timestamp}
                    </span>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400">{log.detail}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 3: Backend Bridge, ADR & Section 26.F Audit */}
      {insideTab === 'backend-adr' && (
        <BackendArchitecturePanel
          activeIndex={activeIndex}
          backendConfig={backendConfig}
          onUpdateBackendConfig={onUpdateBackendConfig}
        />
      )}

      {/* Inside Tab 4: Preferences */}
      {insideTab === 'preferences' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4 max-w-2xl">
          <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
            Desk & Display Preferences
          </h2>
          <div className="divide-y divide-slate-200 dark:divide-slate-800 text-xs">
            <div className="py-3 flex items-center justify-between">
              <div>
                <div className="font-semibold text-slate-900 dark:text-white">Active Desk Account</div>
                <div className="text-slate-500">{session.email} · {session.role}</div>
              </div>
              <span className="font-mono text-emerald-600 dark:text-emerald-400">
                {session.mfaVerified ? '2FA Verified' : 'Standard Session'}
              </span>
            </div>

            <div className="py-3 flex items-center justify-between">
              <div>
                <div className="font-semibold text-slate-900 dark:text-white">Interface Theme</div>
                <div className="text-slate-500">Comfortable Midnight Graphite (Dark) or Cool Mist (Light)</div>
              </div>
              <button
                type="button"
                onClick={onToggleTheme}
                className="min-h-[38px] px-3.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 font-medium cursor-pointer"
              >
                Switch to {isDark ? 'Light Mode' : 'Dark Mode'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
