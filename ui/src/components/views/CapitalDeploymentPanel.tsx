import React, { useState } from 'react';
import {
  Lock,
  Play,
  Pause,
  Power,
  Plus,
  Minus,
  CheckCircle2,
  AlertTriangle,
  Cpu,
  ShieldCheck,
} from 'lucide-react';
import {
  ActivePosition,
  AutomationMode,
  BrokerConnection,
  CapitalDeploymentConfig,
  CapitalLedger,
  CapitalReservation,
  IndexMasterSpec,
  IndexSymbol,
  RiskLimits,
  StrategyAllocation,
  TradeOpportunity,
} from '../../types/pie';
import {
  calculateStrategyLotSize,
  checkLiveAutomationPrerequisites,
  getCapitalDeploymentSummary,
  selectBestAutomatedOpportunity,
} from '../../engine/capitalAllocationEngine';

interface CapitalDeploymentPanelProps {
  config: CapitalDeploymentConfig;
  onUpdateConfig: (patch: Partial<CapitalDeploymentConfig>) => void;
  onAdjustAuthorizedAllocation: (nextAmountInr: number) => void;
  strategies: StrategyAllocation[];
  onUpdateStrategy: (id: string, patch: Partial<StrategyAllocation>) => void;
  reservations: CapitalReservation[];
  activePositions: ActivePosition[];
  ledger: CapitalLedger;
  unrealizedPnlInr: number;
  limits: RiskLimits;
  brokers: BrokerConnection[];
  opportunities: TradeOpportunity[];
  indices: Record<IndexSymbol, IndexMasterSpec>;
  onRunAutomatedCycle: () => void;
  onConfirmReservationFill: (reservationId: string, partialRatio?: number) => void;
  onReleaseReservation: (reservationId: string) => void;
  onToggleEmergencyStop: () => void;
}

const AUTOMATION_MODES: { id: AutomationMode; desc: string }[] = [
  { id: 'Analysis Only', desc: 'Signals & sizing only; zero orders' },
  { id: 'Paper Automation', desc: 'Auto-executes simulated paper orders' },
  { id: 'Live Manual Approval', desc: 'Stages order; requires user click' },
  { id: 'Live Automated', desc: 'Auto-routes live under strict gates' },
];

export const CapitalDeploymentPanel: React.FC<CapitalDeploymentPanelProps> = ({
  config,
  onUpdateConfig,
  onAdjustAuthorizedAllocation,
  strategies,
  onUpdateStrategy,
  reservations,
  activePositions,
  ledger,
  unrealizedPnlInr,
  limits,
  brokers,
  opportunities,
  indices,
  onRunAutomatedCycle,
  onConfirmReservationFill,
  onReleaseReservation,
  onToggleEmergencyStop,
}) => {
  const [selectedStrategyId, setSelectedStrategyId] = useState<string>(strategies[0]?.id ?? '');

  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations,
    activePositions,
    ledger,
  });

  const liveReadiness = checkLiveAutomationPrerequisites({
    config,
    strategies,
    brokers,
    limits,
  });

  const autoPick = selectBestAutomatedOpportunity({
    opportunities,
    strategies,
    indices,
  });

  const inspectedStrategy =
    strategies.find((s) => s.id === selectedStrategyId) ?? autoPick.strategy ?? strategies[0];
  const inspectedOpportunity =
    opportunities.find((o) =>
      inspectedStrategy?.index === 'ALL'
        ? o.suitabilityRank === 1
        : o.index === inspectedStrategy?.index
    ) ?? opportunities[0];

  const sizingPreview =
    inspectedStrategy && inspectedOpportunity
      ? calculateStrategyLotSize({
          opportunity: inspectedOpportunity,
          strategy: inspectedStrategy,
          config,
          summary,
          activePositions,
          limits,
        })
      : null;

  return (
    <div className="space-y-5">
      {/* 1. Three Ring-Fenced Capital Balances */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-slate-200 dark:divide-slate-800">
        <div className="p-4 sm:p-5 space-y-2">
          <div className="text-xs text-slate-500 dark:text-slate-400">Total User Capital</div>
          <div className="font-mono text-2xl font-bold tabular-nums text-slate-900 dark:text-white">
            ₹{summary.totalDeclaredCapitalInr.toLocaleString('en-IN')}
          </div>
          <div className="flex items-center gap-2 text-[11px] text-slate-500">
            <span>Declared account equity</span>
            <button
              type="button"
              onClick={() =>
                onUpdateConfig({
                  totalDeclaredCapitalInr: config.totalDeclaredCapitalInr + 100000,
                })
              }
              data-tip="Increase declared total user equity by ₹1,00,000 (added to undeployed ring-fenced funds)."
              className="text-sky-600 dark:text-sky-400 hover:underline cursor-pointer"
            >
              +₹1L
            </button>
          </div>
        </div>

        <div className="p-4 sm:p-5 space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-sky-600 dark:text-sky-400 font-semibold">
              PIE Trader Authorized Allocation
            </span>
            <span className="font-mono tabular-nums text-slate-500">
              {Math.round((summary.authorizedAllocationInr / Math.max(1, summary.totalDeclaredCapitalInr)) * 100)}% of total
            </span>
          </div>
          <div className="font-mono text-2xl font-bold tabular-nums text-sky-600 dark:text-sky-400">
            ₹{summary.authorizedAllocationInr.toLocaleString('en-IN')}
          </div>
          <div className="flex items-center gap-2 pt-0.5">
            <button
              type="button"
              onClick={() => onAdjustAuthorizedAllocation(config.authorizedAllocationInr - 25000)}
              data-tip="Reduce PIE Trader authorized pool by ₹25,000. Blocked automatically if below deployed + reserved capital."
              className="min-h-[34px] px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 text-xs font-medium inline-flex items-center gap-1 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
            >
              <Minus className="w-3 h-3" /> Reduce ₹25k
            </button>
            <button
              type="button"
              onClick={() => onAdjustAuthorizedAllocation(config.authorizedAllocationInr + 25000)}
              data-tip="Authorize ₹25,000 more from undeployed capital for PIE Trader strategies."
              className="min-h-[34px] px-2.5 py-1 rounded-md border border-slate-200 dark:border-slate-700 text-xs font-medium inline-flex items-center gap-1 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
            >
              <Plus className="w-3 h-3" /> Increase ₹25k
            </button>
          </div>
        </div>

        <div className="p-4 sm:p-5 space-y-2">
          <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
            <Lock className="w-3.5 h-3.5 text-amber-500" />
            <span>Capital Not Authorized for PIE</span>
          </div>
          <div className="font-mono text-2xl font-bold tabular-nums text-slate-700 dark:text-slate-300">
            ₹{summary.undeployedNotAuthorizedInr.toLocaleString('en-IN')}
          </div>
          <div className="text-[11px] text-slate-500 dark:text-slate-400">
            Hard-locked. Never touched by strategies or auto-execution.
          </div>
        </div>
      </div>

      {/* 2. Live Execution Metrics Bar */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 divide-y sm:divide-y-0 sm:divide-x divide-slate-200 dark:divide-slate-800 text-xs">
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Deployed Capital</div>
          <div className="font-mono text-sm font-bold tabular-nums text-slate-900 dark:text-white">
            ₹{summary.deployedCapitalInr.toLocaleString('en-IN')}
          </div>
        </div>
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Reserved Capital</div>
          <div className="font-mono text-sm font-bold tabular-nums text-amber-600 dark:text-amber-400">
            ₹{summary.reservedCapitalInr.toLocaleString('en-IN')}
          </div>
        </div>
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Available Allocation</div>
          <div className="font-mono text-sm font-bold tabular-nums text-sky-600 dark:text-sky-400">
            ₹{summary.availableAllocationInr.toLocaleString('en-IN')}
          </div>
        </div>
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Open Positions</div>
          <div className="font-mono text-sm font-bold tabular-nums text-slate-900 dark:text-white">
            {summary.openPositionsCount} / {config.maxOpenPositions}
          </div>
        </div>
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Today Realized / MTM</div>
          <div
            className={`font-mono text-sm font-bold tabular-nums ${
              ledger.realizedPnlInr + unrealizedPnlInr >= 0
                ? 'text-sky-600 dark:text-sky-400'
                : 'text-rose-600 dark:text-rose-400'
            }`}
          >
            {ledger.realizedPnlInr >= 0 ? '+' : ''}₹{ledger.realizedPnlInr.toLocaleString('en-IN')} /{' '}
            {unrealizedPnlInr >= 0 ? '+' : ''}₹{unrealizedPnlInr.toLocaleString('en-IN')}
          </div>
        </div>
        <div className="p-3.5 space-y-0.5">
          <div className="text-slate-500">Remaining Daily Risk</div>
          <div className="font-mono text-sm font-bold tabular-nums text-sky-600 dark:text-sky-400">
            ₹{summary.remainingDailyRiskInr.toLocaleString('en-IN')}
          </div>
        </div>
      </div>

      {/* 3. Automation Mode & Master Controls */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div>
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
              Automation Mode & Execution Control
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Controlled workflow: Market Intel → Contract Selection → PositionSizingEngine → Capital Reservation → RiskManager → Execution.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => onUpdateConfig({ automationPaused: !config.automationPaused })}
              data-tip="Temporarily pause or resume automated trade selection without closing existing positions."
              className={`min-h-[40px] px-3.5 py-2 rounded-lg border text-xs font-semibold inline-flex items-center gap-1.5 cursor-pointer ${
                config.automationPaused
                  ? 'border-emerald-500/40 text-emerald-600 dark:text-emerald-400'
                  : 'border-amber-500/40 text-amber-600 dark:text-amber-400'
              }`}
            >
              {config.automationPaused ? (
                <>
                  <Play className="w-3.5 h-3.5" /> Resume Automation
                </>
              ) : (
                <>
                  <Pause className="w-3.5 h-3.5" /> Pause Automation
                </>
              )}
            </button>

            <button
              type="button"
              onClick={onToggleEmergencyStop}
              data-tip-title="Emergency Stop (Kill Switch)"
              data-tip="Immediately blocks all automated reservations, strategy orders, and live execution across the platform."
              className={`min-h-[40px] px-3.5 py-2 rounded-lg text-xs font-semibold inline-flex items-center gap-1.5 cursor-pointer ${
                limits.killSwitchEngaged
                  ? 'bg-rose-600 text-white'
                  : 'border border-rose-500/40 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10'
              }`}
            >
              <Power className="w-3.5 h-3.5" />
              <span>{limits.killSwitchEngaged ? 'Emergency Stop Active' : 'Emergency Stop'}</span>
            </button>

            <button
              type="button"
              onClick={onRunAutomatedCycle}
              disabled={limits.killSwitchEngaged || config.automationPaused}
              data-tip-title="Auto-Select & Size Cycle"
              data-tip="Scans opportunities, matches an enabled strategy, computes PositionSizingEngine lots, reserves capital atomically, and routes per current Automation Mode."
              className="min-h-[40px] px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
            >
              <Cpu className="w-3.5 h-3.5" />
              <span>Run Auto-Select & Size Cycle</span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
          {AUTOMATION_MODES.map((m) => {
            const active = config.automationMode === m.id;
            return (
              <button
                key={m.id}
                type="button"
                onClick={() => onUpdateConfig({ automationMode: m.id })}
                data-tip-title={m.id}
                data-tip={m.desc}
                className={`p-3 rounded-xl border text-left transition-colors cursor-pointer ${
                  active
                    ? 'border-sky-500 bg-sky-500/10 text-slate-900 dark:text-white'
                    : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/40'
                }`}
              >
                <div className="text-xs font-bold">{m.id}</div>
                <div className="text-[11px] opacity-80 mt-0.5">{m.desc}</div>
              </button>
            );
          })}
        </div>

        {config.automationMode === 'Live Automated' && (
          <div className="p-3.5 rounded-xl border border-amber-500/40 bg-amber-500/8 text-xs space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold text-amber-700 dark:text-amber-300 flex items-center gap-1.5">
                {liveReadiness.ready ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                ) : (
                  <AlertTriangle className="w-4 h-4" />
                )}
                Live Automated Prerequisites ({liveReadiness.ready ? 'All Satisfied' : `${liveReadiness.missing.length} Pending`})
              </span>
              <label className="inline-flex items-center gap-2 cursor-pointer text-slate-700 dark:text-slate-200">
                <input
                  type="checkbox"
                  checked={config.liveAutomationConsent}
                  onChange={(e) => onUpdateConfig({ liveAutomationConsent: e.target.checked })}
                />
                <span>Explicit Live Execution Consent</span>
              </label>
            </div>
            {!liveReadiness.ready && (
              <div className="text-[11px] text-amber-700 dark:text-amber-300">
                Missing: {liveReadiness.missing.join(' · ')}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 4. Strategy Allocation & PositionSizingEngine Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Strategy-Based Capital Allocation */}
        <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                Strategy-Based Capital Allocation
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Allocated ₹{summary.totalStrategyAllocatedInr.toLocaleString('en-IN')} / ₹
                {summary.authorizedAllocationInr.toLocaleString('en-IN')} authorized. Unused funds stay isolated unless explicitly shared.
              </p>
            </div>
          </div>

          <div className="divide-y divide-slate-200 dark:divide-slate-800">
            {strategies.map((s) => {
              const remaining = Math.max(0, s.allocatedInr - s.deployedInr - s.reservedInr);
              const isSelected = inspectedStrategy?.id === s.id;
              return (
                <div
                  key={s.id}
                  onClick={() => setSelectedStrategyId(s.id)}
                  className={`p-4 space-y-2.5 cursor-pointer transition-colors ${
                    isSelected ? 'bg-sky-500/6' : 'hover:bg-slate-50 dark:hover:bg-slate-800/30'
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <div className="font-display font-bold text-sm text-slate-900 dark:text-white">
                        {s.name}{' '}
                        <span className="font-sans font-normal text-xs text-slate-500">
                          · {s.index}
                        </span>
                      </div>
                      <div className="text-[11px] font-mono tabular-nums text-slate-500">
                        Deployed ₹{s.deployedInr.toLocaleString('en-IN')} · Reserved ₹
                        {s.reservedInr.toLocaleString('en-IN')} · Remaining ₹
                        {remaining.toLocaleString('en-IN')}
                      </div>
                    </div>

                    <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                      <button
                        type="button"
                        onClick={() => onUpdateStrategy(s.id, { enabled: !s.enabled })}
                        data-tip="Enable or disable automated lot sizing & trade selection for this strategy sleeve."
                        className={`min-h-[32px] px-2.5 py-1 rounded-md text-xs font-semibold cursor-pointer ${
                          s.enabled
                            ? 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400'
                            : 'bg-slate-200 dark:bg-slate-800 text-slate-500'
                        }`}
                      >
                        {s.enabled ? 'Enabled' : 'Disabled'}
                      </button>
                    </div>
                  </div>

                  <div
                    className="flex flex-wrap items-center justify-between gap-3 text-xs pt-1"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="flex items-center gap-2 font-mono tabular-nums">
                      <span className="font-sans text-slate-500">Allocation:</span>
                      <button
                        type="button"
                        onClick={() =>
                          onUpdateStrategy(s.id, {
                            allocatedInr: Math.max(s.deployedInr + s.reservedInr, s.allocatedInr - 10000),
                          })
                        }
                        data-tip="Decrease this strategy's ring-fenced allocation by ₹10,000 (cannot drop below committed funds)."
                        className="px-2 py-0.5 rounded border border-slate-200 dark:border-slate-700 cursor-pointer"
                      >
                        -10k
                      </button>
                      <span className="font-bold text-slate-900 dark:text-white">
                        ₹{s.allocatedInr.toLocaleString('en-IN')}
                      </span>
                      <button
                        type="button"
                        disabled={summary.unassignedAuthorizedInr < 10000}
                        onClick={() =>
                          onUpdateStrategy(s.id, { allocatedInr: s.allocatedInr + 10000 })
                        }
                        data-tip="Allocate ₹10,000 more from unassigned authorized capital to this strategy."
                        className="px-2 py-0.5 rounded border border-slate-200 dark:border-slate-700 disabled:opacity-40 cursor-pointer"
                      >
                        +10k
                      </button>
                    </div>

                    <div className="flex items-center gap-4 text-[11px]">
                      <label className="inline-flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={s.allowAddOns}
                          onChange={(e) => onUpdateStrategy(s.id, { allowAddOns: e.target.checked })}
                        />
                        <span>Add-ons</span>
                      </label>
                      <label className="inline-flex items-center gap-1.5 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={s.explicitReallocationAllowed}
                          onChange={(e) =>
                            onUpdateStrategy(s.id, { explicitReallocationAllowed: e.target.checked })
                          }
                        />
                        <span>Cross-Strategy Borrow</span>
                      </label>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Automatic PositionSizingEngine Inspector */}
        <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
          <div>
            <div className="text-xs text-sky-600 dark:text-sky-400 font-semibold flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4" />
              Automatic PositionSizingEngine
            </div>
            <h3 className="font-display text-base font-bold text-slate-900 dark:text-white mt-0.5">
              {inspectedStrategy?.name} → {inspectedOpportunity?.contract.tradingSymbol}
            </h3>
          </div>

          {sizingPreview && (
            <>
              <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800 flex items-center justify-between">
                <div>
                  <div className="text-xs text-slate-500">Calculated Eligible Size</div>
                  <div className="font-mono text-2xl font-bold tabular-nums text-slate-900 dark:text-white">
                    {sizingPreview.eligibleLots} Lot(s){' '}
                    <span className="text-xs font-normal text-slate-500">
                      ({sizingPreview.eligibleLots * inspectedOpportunity.contract.lotSize} qty)
                    </span>
                  </div>
                </div>
                <div className="text-right text-xs">
                  <div className="text-slate-500">Limiting Factor</div>
                  <div className="font-semibold text-sky-600 dark:text-sky-400">
                    {sizingPreview.limitingFactor}
                  </div>
                </div>
              </div>

              <div className="space-y-2 text-xs font-mono tabular-nums">
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Risk per Lot (SL + Charges + Slippage)</span>
                  <span className="font-semibold">₹{sizingPreview.riskPerLotInr.toLocaleString('en-IN')}</span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Capital Required per Lot</span>
                  <span className="font-semibold">₹{sizingPreview.capitalPerLotInr.toLocaleString('en-IN')}</span>
                </div>
                <div className="pt-2 border-t border-slate-200 dark:border-slate-800 grid grid-cols-2 gap-2 text-[11px]">
                  <div>Risk-Based Max: {sizingPreview.riskBasedLots} lots</div>
                  <div>Capital-Based Max: {sizingPreview.capitalBasedLots} lots</div>
                  <div>Strategy-Based Max: {sizingPreview.strategyBasedLots} lots</div>
                  <div>Index Cap Max: {sizingPreview.indexExposureLots} lots</div>
                  <div>Exchange Freeze: {sizingPreview.brokerExchangeLots} lots</div>
                  <div>Daily Risk Max: {sizingPreview.portfolioRiskLots} lots</div>
                </div>
              </div>

              {sizingPreview.rejectedReason && (
                <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-xs text-rose-600 dark:text-rose-300">
                  {sizingPreview.rejectedReason}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* 5. Capital Reservations & Partial Fill Reconciliation */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
        <div className="px-5 py-3.5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
          <div>
            <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
              Capital Reservations & Pre-Execution Ledger
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Prevents concurrent signals from reserving the same capital twice; rejected orders release reservations automatically.
            </p>
          </div>
          <span className="text-xs font-mono text-slate-500">{reservations.length} record(s)</span>
        </div>

        {reservations.length === 0 ? (
          <div className="p-6 text-center text-xs text-slate-500 dark:text-slate-400">
            No pending capital reservations. Click <strong>Run Auto-Select & Size Cycle</strong> above to test reservation, partial fill reconciliation, or rejection release.
          </div>
        ) : (
          <div className="divide-y divide-slate-200 dark:divide-slate-800">
            {reservations.map((r) => (
              <div
                key={r.id}
                className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
              >
                <div className="space-y-0.5">
                  <div className="font-semibold text-slate-900 dark:text-white">
                    {r.tradingSymbol} · {r.requestedLots} lot(s) ({r.filledLots} filled) ·{' '}
                    <span
                      className={
                        r.status === 'Filled'
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : r.status === 'Rejected' || r.status === 'Released'
                          ? 'text-slate-500'
                          : 'text-amber-600 dark:text-amber-400'
                      }
                    >
                      {r.status}
                    </span>
                  </div>
                  <div className="font-mono text-[11px] text-slate-500">
                    Key: {r.idempotencyKey} · Reserved Cap: ₹{r.reservedCapitalInr.toLocaleString('en-IN')} · Reserved Risk: ₹{r.reservedRiskInr.toLocaleString('en-IN')}
                    {r.reason ? ` · ${r.reason}` : ''}
                  </div>
                </div>

                {(r.status === 'Reserved' || r.status === 'Partially Filled') && (
                  <div className="flex flex-wrap items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => onConfirmReservationFill(r.id, 0.5)}
                      data-tip="Reconcile a 50% partial fill: moves filled margin to Deployed while keeping remaining reservation tracked."
                      className="min-h-[34px] px-2.5 py-1 rounded-md border border-sky-500/40 text-sky-600 dark:text-sky-400 font-medium cursor-pointer"
                    >
                      Partial Fill (50%)
                    </button>
                    <button
                      type="button"
                      onClick={() => onConfirmReservationFill(r.id, 1)}
                      data-tip="Confirm full fill: converts reserved capital into an active position."
                      className="min-h-[34px] px-2.5 py-1 rounded-md bg-emerald-600 text-white font-medium cursor-pointer"
                    >
                      Confirm Fill
                    </button>
                    <button
                      type="button"
                      onClick={() => onReleaseReservation(r.id)}
                      data-tip="Cancel or reject pending reservation and immediately refund reserved capital back to the strategy."
                      className="min-h-[34px] px-2.5 py-1 rounded-md border border-rose-500/40 text-rose-600 dark:text-rose-400 font-medium cursor-pointer"
                    >
                      Reject & Release
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 6. Granular Capital & Risk Limits Configuration */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
        <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
          Capital Limits & Exposure Thresholds
        </h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          {[
            { label: 'Max / Strategy (₹)', key: 'maxCapitalPerStrategyInr' as const, val: config.maxCapitalPerStrategyInr, step: 5000, min: 0 },
            { label: 'Max / Index (₹)', key: 'maxCapitalPerIndexInr' as const, val: config.maxCapitalPerIndexInr, step: 5000, min: 0 },
            { label: 'Max / Basket (₹)', key: 'maxCapitalPerBasketInr' as const, val: config.maxCapitalPerBasketInr, step: 5000, min: 0 },
            { label: 'Max / Trade (₹)', key: 'maxCapitalPerTradeInr' as const, val: config.maxCapitalPerTradeInr, step: 1000, min: 0 },
            { label: 'Max Risk / Trade (₹)', key: 'maxRiskPerTradeInr' as const, val: config.maxRiskPerTradeInr, step: 500, min: 0 },
            { label: 'Daily Loss Limit (₹)', key: 'dailyLossLimitInr' as const, val: config.dailyLossLimitInr, step: 1000, min: 0 },
            { label: 'Max Daily Exposure (₹)', key: 'maxDailyExposureInr' as const, val: config.maxDailyExposureInr, step: 5000, min: 0 },
            { label: 'Max Open Positions', key: 'maxOpenPositions' as const, val: config.maxOpenPositions, step: 1, min: 1 },
          ].map((field) => (
            <div key={field.key}>
              <label className="block text-[11px] text-slate-500 mb-1">{field.label}</label>
              <input
                type="number"
                step={field.step}
                min={field.min}
                value={field.val}
                onChange={(e) =>
                  onUpdateConfig({ [field.key]: Math.max(field.min, Number(e.target.value)) })
                }
                className="w-full min-h-[38px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono tabular-nums"
              />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
