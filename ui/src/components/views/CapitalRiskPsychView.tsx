import React, { useState } from 'react';
import { ShieldAlert, Brain, CheckCircle2, Lock, FileSpreadsheet, Copy, Check, X } from 'lucide-react';
import {
  ActivePosition,
  BacktestStrategySummary,
  BrokerConnection,
  CapitalDeploymentConfig,
  CapitalInsideTab,
  CapitalLedger,
  CapitalReservation,
  IndexMasterSpec,
  IndexSymbol,
  JournalEntry,
  PsychologyLog,
  RiskLimits,
  RiskProfile,
  StrategyAllocation,
  TradeOpportunity,
} from '../../types/pie';
import { computeNetEquity } from '../../engine/pieEngine';
import { DAILY_PNL_30_SESSIONS } from '../../data/pieMarketData';
import { EquityCurveChart } from '../charts/IndexCharts';
import { CumulativePnlChart } from '../charts/CumulativePnlChart';
import { CapitalDeploymentPanel } from './CapitalDeploymentPanel';

interface CapitalRiskPsychViewProps {
  insideTab: CapitalInsideTab;
  ledger: CapitalLedger;
  activePositions: ActivePosition[];
  limits: RiskLimits;
  onChangeRiskProfile: (profile: RiskProfile) => void;
  onUpdateLimits: (patch: Partial<RiskLimits>) => void;
  psychology: PsychologyLog;
  onUpdateMood: (mood: PsychologyLog['selfAssessmentMood']) => void;
  onToggleCooldownLock: () => void;
  backtests: BacktestStrategySummary[];
  journal: JournalEntry[];
  deploymentConfig: CapitalDeploymentConfig;
  onUpdateDeploymentConfig: (patch: Partial<CapitalDeploymentConfig>) => void;
  onAdjustAuthorizedAllocation: (nextAmountInr: number) => void;
  strategies: StrategyAllocation[];
  onUpdateStrategy: (id: string, patch: Partial<StrategyAllocation>) => void;
  reservations: CapitalReservation[];
  brokers: BrokerConnection[];
  opportunities: TradeOpportunity[];
  indices: Record<IndexSymbol, IndexMasterSpec>;
  onRunAutomatedCycle: () => void;
  onConfirmReservationFill: (reservationId: string, partialRatio?: number) => void;
  onReleaseReservation: (reservationId: string) => void;
  onToggleEmergencyStop: () => void;
}

export const CapitalRiskPsychView: React.FC<CapitalRiskPsychViewProps> = ({
  insideTab,
  ledger,
  activePositions,
  limits,
  onChangeRiskProfile,
  onUpdateLimits,
  psychology,
  onUpdateMood,
  onToggleCooldownLock,
  backtests,
  journal,
  deploymentConfig,
  onUpdateDeploymentConfig,
  onAdjustAuthorizedAllocation,
  strategies,
  onUpdateStrategy,
  reservations,
  brokers,
  opportunities,
  indices,
  onRunAutomatedCycle,
  onConfirmReservationFill,
  onReleaseReservation,
  onToggleEmergencyStop,
}) => {
  const [csvModalText, setCsvModalText] = useState<string | null>(null);
  const [copiedCsv, setCopiedCsv] = useState(false);

  const equityStats = computeNetEquity(ledger, activePositions);

  const escapeCsv = (value: string | number | boolean): string => {
    const str = String(value);
    return /[",\n]/.test(str) ? `"${str.replace(/"/g, '""')}"` : str;
  };

  const handleGenerateLedgerCsv = () => {
    const liveSessionPnl = Number((ledger.realizedPnlInr + equityStats.unrealizedPnlInr).toFixed(2));
    const priorSessions = DAILY_PNL_30_SESSIONS.slice(0, -1);
    const priorCum = priorSessions.length ? priorSessions[priorSessions.length - 1].cumulativePnlInr : 0;
    const peakPrior = priorSessions.reduce((m, s) => Math.max(m, s.cumulativePnlInr), 0);
    const liveCum = Number((priorCum + liveSessionPnl).toFixed(2));
    const livePeak = Math.max(peakPrior, liveCum);
    const lastTemplate = DAILY_PNL_30_SESSIONS[DAILY_PNL_30_SESSIONS.length - 1];
    const resolvedSessions = [
      ...priorSessions,
      {
        ...lastTemplate,
        dailyNetPnlInr: liveSessionPnl,
        cumulativePnlInr: liveCum,
        drawdownFromPeakInr: Number((livePeak - liveCum).toFixed(2)),
      },
    ];

    const lines: string[] = [
      'SECTION,METRIC_OR_DATE,SYMBOL_OR_INDEX,VALUE_1,VALUE_2,VALUE_3,VALUE_4,VALUE_5,NOTES',
      [
        'CAPITAL_LEDGER',
        new Date().toISOString(),
        limits.profile,
        `NetEquity=${equityStats.netEquityInr}`,
        `AvailableCash=${equityStats.withdrawableCashInr}`,
        `Deployed=${ledger.deployedCapitalInr}`,
        `Reserved=${ledger.reservedCapitalInr}`,
        `RealizedPnL=${ledger.realizedPnlInr}`,
        `UnrealizedMTM=${equityStats.unrealizedPnlInr} | BrokerageAndTaxes=${ledger.brokerageAndTaxesInr} | DrawdownPct=${equityStats.drawdownPct}`,
      ]
        .map(escapeCsv)
        .join(','),
      '',
      'SESSION_NO,DATE,TRADES,DAILY_NET_PNL_INR,CUMULATIVE_PNL_INR,DRAWDOWN_FROM_PEAK_INR',
      ...resolvedSessions.map((s) =>
        [
          s.sessionIndex,
          s.dateLabel,
          s.tradesCount,
          s.dailyNetPnlInr,
          s.cumulativePnlInr,
          s.drawdownFromPeakInr,
        ]
          .map(escapeCsv)
          .join(',')
      ),
      '',
      'JOURNAL_TIMESTAMP,INDEX,TRADING_SYMBOL,SETUP,LOTS,PLANNED_ENTRY,ACTUAL_ENTRY,ACTUAL_SL,NET_PNL_INR,RULE_FOLLOWED,NOTES',
      ...journal.map((j) =>
        [
          j.timestamp,
          j.index,
          j.tradingSymbol,
          j.setup,
          j.lots,
          j.plannedEntry,
          j.actualEntry,
          j.actualSl,
          j.netPnlInr,
          j.ruleFollowed,
          j.notes,
        ]
          .map(escapeCsv)
          .join(',')
      ),
    ];

    setCopiedCsv(false);
    setCsvModalText(lines.join('\n'));
  };

  const handleCopyCsv = async () => {
    if (!csvModalText) return;
    try {
      await navigator.clipboard.writeText(csvModalText);
      setCopiedCsv(true);
      setTimeout(() => setCopiedCsv(false), 3000);
    } catch {
      const textarea = document.getElementById('ledger-csv-audit-output') as HTMLTextAreaElement | null;
      if (textarea) {
        textarea.focus();
        textarea.select();
        document.execCommand('copy');
        setCopiedCsv(true);
        setTimeout(() => setCopiedCsv(false), 3000);
      }
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-bold text-slate-900 dark:text-white">
            Capital Ledger, Risk Guardrails & Trading Discipline
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Single source of truth for equity, fees, non-bypassable risk controls, behavioural discipline, and out-of-sample backtests.
          </p>
        </div>
        {insideTab === 'ledger' && (
          <button
            type="button"
            onClick={handleGenerateLedgerCsv}
            data-tip="Generate a CSV-formatted audit snapshot of Capital Ledger, 30-session daily P&L, and Trade Journal entries."
            className="min-h-[40px] px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#111827] hover:bg-slate-100 dark:hover:bg-slate-800 text-xs font-semibold text-slate-800 dark:text-slate-100 inline-flex items-center gap-2 self-start sm:self-auto whitespace-nowrap shrink-0 transition-colors cursor-pointer"
          >
            <FileSpreadsheet className="w-4 h-4 text-sky-500" />
            <span>Export Ledger CSV</span>
          </button>
        )}
      </div>

      {/* Inside Tab 0: Capital Deployment & Automated Execution */}
      {insideTab === 'deployment' && (
        <CapitalDeploymentPanel
          config={deploymentConfig}
          onUpdateConfig={onUpdateDeploymentConfig}
          onAdjustAuthorizedAllocation={onAdjustAuthorizedAllocation}
          strategies={strategies}
          onUpdateStrategy={onUpdateStrategy}
          reservations={reservations}
          activePositions={activePositions}
          ledger={ledger}
          unrealizedPnlInr={equityStats.unrealizedPnlInr}
          limits={limits}
          brokers={brokers}
          opportunities={opportunities}
          indices={indices}
          onRunAutomatedCycle={onRunAutomatedCycle}
          onConfirmReservationFill={onConfirmReservationFill}
          onReleaseReservation={onReleaseReservation}
          onToggleEmergencyStop={onToggleEmergencyStop}
        />
      )}

      {/* Inside Tab 1: Capital Ledger */}
      {insideTab === 'ledger' && (
        <div className="space-y-5">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] grid grid-cols-2 lg:grid-cols-4 divide-y lg:divide-y-0 lg:divide-x divide-slate-200 dark:divide-slate-800">
            <div className="p-4 sm:p-5 space-y-1">
              <div className="text-xs text-slate-500 dark:text-slate-400">Net Equity</div>
              <div className="font-mono text-xl font-bold tabular-nums text-slate-900 dark:text-white">
                ₹{equityStats.netEquityInr.toLocaleString('en-IN')}
              </div>
              <div className="text-[11px] font-mono text-slate-500">
                Peak ₹{ledger.peakEquityInr.toLocaleString('en-IN')} · DD {equityStats.drawdownPct}%
              </div>
            </div>

            <div className="p-4 sm:p-5 space-y-1">
              <div className="text-xs text-slate-500 dark:text-slate-400">
                Withdrawable / Available Cash
              </div>
              <div className="font-mono text-xl font-bold tabular-nums text-sky-600 dark:text-sky-400">
                ₹{equityStats.withdrawableCashInr.toLocaleString('en-IN')}
              </div>
              <div className="text-[11px] text-slate-500">
                Excludes unrealized profit
              </div>
            </div>

            <div className="p-4 sm:p-5 space-y-1">
              <div className="text-xs text-slate-500 dark:text-slate-400">Realized / Unrealized P&L</div>
              <div
                className={`font-mono text-xl font-bold tabular-nums ${
                  ledger.realizedPnlInr >= 0
                    ? 'text-sky-600 dark:text-sky-400'
                    : 'text-rose-600 dark:text-rose-400'
                }`}
              >
                {ledger.realizedPnlInr >= 0 ? '+' : ''}₹{ledger.realizedPnlInr.toLocaleString('en-IN')}
              </div>
              <div
                className={`text-[11px] font-mono tabular-nums ${
                  equityStats.unrealizedPnlInr >= 0
                    ? 'text-sky-600 dark:text-sky-400'
                    : 'text-rose-600 dark:text-rose-400'
                }`}
              >
                Open MTM: {equityStats.unrealizedPnlInr >= 0 ? '+' : ''}₹
                {equityStats.unrealizedPnlInr.toLocaleString('en-IN')}
              </div>
            </div>

            <div className="p-4 sm:p-5 space-y-1">
              <div className="text-xs text-slate-500 dark:text-slate-400">Deployed / Reserved</div>
              <div className="font-mono text-xl font-bold tabular-nums text-sky-600 dark:text-sky-400">
                ₹{(ledger.deployedCapitalInr + ledger.reservedCapitalInr).toLocaleString('en-IN')}
              </div>
              <div className="text-[11px] font-mono text-slate-500">
                Brokerage & Taxes: ₹{ledger.brokerageAndTaxesInr.toFixed(2)}
              </div>
            </div>
          </div>

          <CumulativePnlChart
            sessions={DAILY_PNL_30_SESSIONS}
            liveSessionNetPnlInr={ledger.realizedPnlInr + equityStats.unrealizedPnlInr}
          />

          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-3 text-xs">
            <h2 className="font-display text-sm font-bold text-slate-900 dark:text-white">
              Dynamic Position Sizing Formula
            </h2>
            <p className="text-slate-600 dark:text-slate-300 font-mono">
              Max Eligible Lots = Floor( (Net Equity × {limits.maxRiskPerTradePct}% Risk Budget) ÷ Estimated Loss Per Lot )
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2 border-t border-slate-200 dark:border-slate-800 font-mono tabular-nums">
              <div>
                <div className="font-sans text-slate-500">Per-Trade Risk Budget</div>
                <div className="text-sm font-semibold mt-0.5">
                  ₹{((equityStats.netEquityInr * limits.maxRiskPerTradePct) / 100).toLocaleString('en-IN')}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Per-Basket Risk Cap</div>
                <div className="text-sm font-semibold mt-0.5">
                  ₹{((equityStats.netEquityInr * limits.maxRiskPerBasketPct) / 100).toLocaleString('en-IN')}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Weekly / Monthly P&L</div>
                <div
                  className={`text-sm font-semibold mt-0.5 ${
                    ledger.weeklyPnlInr >= 0
                      ? 'text-sky-600 dark:text-sky-400'
                      : 'text-rose-600 dark:text-rose-400'
                  }`}
                >
                  {ledger.weeklyPnlInr >= 0 ? '+' : ''}₹{ledger.weeklyPnlInr.toLocaleString('en-IN')} ({ledger.monthlyReturnPct}%)
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 2: Risk Guardrails */}
      {insideTab === 'risk' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                  Independent RiskManager Profile
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Defaults to Conservative (0.5% max risk/trade). No AI signal can override a RiskManager rejection.
                </p>
              </div>
              <div className="inline-flex p-1 rounded-lg bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
                {(['Conservative', 'Moderate', 'Aggressive'] as RiskProfile[]).map((p) => (
                  <button
                    key={p}
                    type="button"
                    onClick={() => onChangeRiskProfile(p)}
                    data-tip={`Apply ${p} RiskManager thresholds (max risk/trade, spread limit, and daily loss cap).`}
                    className={`min-h-[34px] px-3 py-1 rounded-md text-xs font-medium cursor-pointer ${
                      limits.profile === p
                        ? 'bg-sky-600 text-white'
                        : 'text-slate-600 dark:text-slate-400'
                    }`}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-xs font-mono tabular-nums pt-2 border-t border-slate-200 dark:border-slate-800">
              <div>
                <div className="font-sans text-slate-500">Max Risk / Trade</div>
                <div className="text-sm font-bold mt-0.5">{limits.maxRiskPerTradePct}%</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Max Risk / Basket</div>
                <div className="text-sm font-bold mt-0.5">{limits.maxRiskPerBasketPct}%</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Daily Loss Limit</div>
                <div className="text-sm font-bold mt-0.5">₹{limits.dailyLossLimitInr.toLocaleString('en-IN')}</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Max Trades / Day</div>
                <div className="text-sm font-bold mt-0.5">
                  {ledger.dailyTradesCount} / {limits.maxTradesPerDay}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Max Bid-Ask Spread</div>
                <div className="text-sm font-bold mt-0.5">{limits.maxSpreadPct}%</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Min Liquidity Score</div>
                <div className="text-sm font-bold mt-0.5">{limits.minLiquidityScore}/100</div>
              </div>
            </div>
          </div>

          <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <ShieldAlert className="w-4 h-4 text-amber-500" />
              Active Circuit Breakers & Locks
            </h3>

            <div className="space-y-3 text-xs">
              <label className="flex items-center justify-between p-3 rounded-lg border border-slate-200 dark:border-slate-800 cursor-pointer">
                <div>
                  <div className="font-semibold text-slate-900 dark:text-white">
                    Event-Risk Execution Lock
                  </div>
                  <div className="text-slate-500">Blocks new entries ahead of RBI / Fed policy windows</div>
                </div>
                <input
                  type="checkbox"
                  checked={limits.eventRiskLock}
                  onChange={(e) => onUpdateLimits({ eventRiskLock: e.target.checked })}
                />
              </label>

              <label className="flex items-center justify-between p-3 rounded-lg border border-slate-200 dark:border-slate-800 cursor-pointer">
                <div>
                  <div className="font-semibold text-slate-900 dark:text-white">
                    Behavioural Cooldown Lock
                  </div>
                  <div className="text-slate-500">Temporarily locks order placement to prevent overtrading</div>
                </div>
                <input
                  type="checkbox"
                  checked={limits.behaviouralCooldownActive}
                  onChange={onToggleCooldownLock}
                />
              </label>
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 3: Trader Psychology & Discipline */}
      {insideTab === 'psychology' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Brain className="w-4 h-4 text-sky-500" />
                <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                  Personal Trading Discipline Report
                </h2>
              </div>
              <span className="font-mono text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                Behavioural Risk: {psychology.behaviouralRiskScore}/100 (Low)
              </span>
            </div>

            <p className="text-xs sm:text-sm text-slate-700 dark:text-slate-300 p-3.5 rounded-lg bg-slate-50 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
              {psychology.dailyReportText}
            </p>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs font-mono tabular-nums pt-1">
              <div>
                <div className="font-sans text-slate-500">Rule Adherence</div>
                <div className="text-base font-bold text-emerald-600 dark:text-emerald-400">
                  {psychology.ruleAdherencePct}%
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">SL Violations</div>
                <div className="text-base font-bold">{psychology.stopLossViolations}</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Overtrading Flags</div>
                <div className="text-base font-bold">{psychology.overtradingCount}</div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Avg Entry Slippage</div>
                <div className="text-base font-bold">{psychology.plannedVsActualSlippagePts} pts</div>
              </div>
            </div>
          </div>

          <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
              Trader Self-Assessment & Cooldown Gate
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Recorded directly from trader input (never inferred solely from market candles).
            </p>

            <div className="grid grid-cols-2 gap-2">
              {(
                [
                  'Calm & Objective',
                  'Slightly Impatient',
                  'FOMO / Chasing',
                  'Fatigued',
                ] as PsychologyLog['selfAssessmentMood'][]
              ).map((mood) => (
                <button
                  key={mood}
                  type="button"
                  onClick={() => onUpdateMood(mood)}
                  data-tip="Log current emotional state. Selecting FOMO/Chasing or Fatigued engages Cooldown Lock automatically."
                  className={`min-h-[42px] px-3 py-2 rounded-lg border text-xs font-medium text-left cursor-pointer ${
                    psychology.selfAssessmentMood === mood
                      ? 'border-sky-500 bg-sky-500/12 text-sky-600 dark:text-sky-300'
                      : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-300'
                  }`}
                >
                  {mood}
                </button>
              ))}
            </div>

            <button
              type="button"
              onClick={onToggleCooldownLock}
              data-tip="Temporarily lock new order routing to prevent impulsive or revenge trades."
              className={`w-full min-h-[44px] px-4 py-2 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 cursor-pointer ${
                limits.behaviouralCooldownActive
                  ? 'bg-amber-600 text-white'
                  : 'border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200'
              }`}
            >
              <Lock className="w-3.5 h-3.5" />
              <span>
                {limits.behaviouralCooldownActive
                  ? 'Cooldown Lock Active (Click to Release)'
                  : 'Engage Voluntary Cooldown Lock'}
              </span>
            </button>
          </div>
        </div>
      )}

      {/* Inside Tab 4: Backtesting & Trade Journal */}
      {insideTab === 'backtest' && (
        <div className="space-y-5">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <div>
              <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                In-Sample vs Validation vs Out-of-Sample Backtest (Costs & Slippage Included)
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Strict walk-forward separation prevents lookahead bias and overfitting.
              </p>
            </div>

            <EquityCurveChart
              curves={[
                { label: 'In-Sample (2022-23)', color: '#94A3B8', data: backtests[0].equityCurve },
                { label: 'Validation (2024)', color: '#38BDF8', data: backtests[1].equityCurve },
                { label: 'Out-of-Sample (2025-26)', color: '#0284C7', data: backtests[2].equityCurve },
              ]}
            />

            <div className="overflow-x-auto">
              <table className="w-full text-xs font-mono tabular-nums">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 text-left">
                    <th className="py-2.5 px-3 font-sans">Split</th>
                    <th className="py-2.5 px-3">Period</th>
                    <th className="py-2.5 px-3 text-right">Trades</th>
                    <th className="py-2.5 px-3 text-right">Win %</th>
                    <th className="py-2.5 px-3 text-right">PF</th>
                    <th className="py-2.5 px-3 text-right">Sharpe</th>
                    <th className="py-2.5 px-3 text-right">Max DD</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                  {backtests.map((b) => (
                    <tr key={b.id}>
                      <td className="py-2.5 px-3 font-sans font-semibold">{b.split}</td>
                      <td className="py-2.5 px-3 font-sans text-slate-500">{b.period}</td>
                      <td className="py-2.5 px-3 text-right">{b.trades}</td>
                      <td className="py-2.5 px-3 text-right">{b.winRatePct}%</td>
                      <td className="py-2.5 px-3 text-right">{b.profitFactor}</td>
                      <td className="py-2.5 px-3 text-right">{b.sharpe}</td>
                      <td className="py-2.5 px-3 text-right text-rose-600 dark:text-rose-400">
                        {b.maxDrawdownPct}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
            <div className="px-5 py-3.5 border-b border-slate-200 dark:border-slate-800">
              <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
                Session Trade Journal (Planned vs Actual)
              </h3>
            </div>
            <div className="divide-y divide-slate-200 dark:divide-slate-800">
              {journal.map((j) => (
                <div key={j.id} className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
                  <div className="space-y-0.5">
                    <div className="font-semibold text-slate-900 dark:text-white">
                      {j.tradingSymbol} · {j.setup}
                    </div>
                    <div className="text-slate-500 dark:text-slate-400">
                      {j.timestamp} · Planned ₹{j.plannedEntry} / Actual ₹{j.actualEntry} · {j.notes}
                    </div>
                  </div>
                  <div className="flex items-center gap-3 shrink-0 font-mono tabular-nums">
                    <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-sans">
                      <CheckCircle2 className="w-3.5 h-3.5" /> {j.ruleFollowed ? 'Rule Followed' : 'Deviation'}
                    </span>
                    <span className={j.netPnlInr >= 0 ? 'text-sky-600 dark:text-sky-400 font-bold' : 'text-rose-600 dark:text-rose-400 font-bold'}>
                      {j.netPnlInr >= 0 ? '+' : ''}₹{j.netPnlInr.toLocaleString('en-IN')}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* CSV Audit Export Prompt Modal */}
      {csvModalText !== null && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4"
          onClick={() => setCsvModalText(null)}
        >
          <div
            className="w-full max-w-2xl rounded-t-3xl sm:rounded-2xl bg-white dark:bg-[#111827] border border-slate-200 dark:border-slate-800 p-5 sm:p-6 space-y-4 max-h-[88vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="text-xs text-slate-500 dark:text-slate-400">
                  External Audit Export · Capital Ledger, 30-Session Trajectory & Journal
                </div>
                <h2 className="font-display text-base sm:text-lg font-bold text-slate-900 dark:text-white mt-0.5">
                  Copy Ledger CSV to Clipboard
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setCsvModalText(null)}
                aria-label="Close CSV export prompt"
                className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-slate-600 dark:text-slate-300">
              Review or copy the CSV-formatted ledger entries below for external reconciliation or spreadsheet auditing.
            </p>

            <textarea
              id="ledger-csv-audit-output"
              readOnly
              value={csvModalText}
              onClick={(e) => e.currentTarget.select()}
              rows={12}
              className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] p-3 text-[11px] font-mono text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-sky-500 resize-none"
            />

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400">
                {csvModalText.split('\n').length} CSV rows generated
              </span>
              <div className="flex items-center gap-2.5">
                <button
                  type="button"
                  onClick={() => setCsvModalText(null)}
                  className="min-h-[42px] px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-700 dark:text-slate-300 cursor-pointer"
                >
                  Close
                </button>
                <button
                  type="button"
                  onClick={handleCopyCsv}
                  className="min-h-[42px] px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center gap-2 cursor-pointer transition-colors"
                >
                  {copiedCsv ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  <span>{copiedCsv ? 'Copied to Clipboard' : 'Copy CSV String'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
