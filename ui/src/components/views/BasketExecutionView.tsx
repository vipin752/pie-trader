import React, { useState } from 'react';
import {
  AlertOctagon,
  CheckCircle2,
  Plus,
  Trash2,
  ShieldAlert,
  TrendingUp,
  Save,
  XCircle,
} from 'lucide-react';
import {
  ActivePosition,
  BasketInsideTab,
  BasketLeg,
  BasketLegKind,
  CapitalLedger,
  IndexMasterSpec,
  IndexSymbol,
  RiskLimits,
} from '../../types/pie';
import { validateBasketWithRiskManager } from '../../engine/pieEngine';

interface BasketExecutionViewProps {
  insideTab: BasketInsideTab;
  onChangeInsideTab: (tab: BasketInsideTab) => void;
  activeIndex: IndexSymbol;
  indices: Record<IndexSymbol, IndexMasterSpec>;
  basketLegs: BasketLeg[];
  onUpdateLeg: (id: string, patch: Partial<BasketLeg>) => void;
  onRemoveLeg: (id: string) => void;
  onAddStagedLeg: (kind: BasketLegKind) => void;
  onSubmitBasketPaper: () => void;
  onSimulatePartialFill: (id: string) => void;
  onCancelPendingLegs: () => void;
  activePositions: ActivePosition[];
  onScaleInPosition: (posId: string, addLots: number, confirmedBreakout: boolean) => void;
  onPartialExitPosition: (posId: string, exitLots: number) => void;
  onAdjustPositionLtp: (posId: string, deltaPts: number) => void;
  onEmergencySquareOffAll: () => void;
  ledger: CapitalLedger;
  limits: RiskLimits;
}

export const BasketExecutionView: React.FC<BasketExecutionViewProps> = ({
  insideTab,
  onChangeInsideTab,
  activeIndex,
  indices,
  basketLegs,
  onUpdateLeg,
  onRemoveLeg,
  onAddStagedLeg,
  onSubmitBasketPaper,
  onSimulatePartialFill,
  onCancelPendingLegs,
  activePositions,
  onScaleInPosition,
  onPartialExitPosition,
  onAdjustPositionLtp,
  onEmergencySquareOffAll,
  ledger,
  limits,
}) => {
  const [savedTemplates, setSavedTemplates] = useState<string[]>([
    'NIFTY 3-Stage Staged Breakout (1+1+1)',
  ]);
  const [templateMessage, setTemplateMessage] = useState('');
  const [confirmedBreakout, setConfirmedBreakout] = useState(true);

  const validation = validateBasketWithRiskManager({
    legs: basketLegs,
    ledger,
    limits,
    activePositions,
  });

  const handleSaveTemplate = () => {
    const name = `${activeIndex} Staged Basket (${basketLegs.length} legs)`;
    if (!savedTemplates.includes(name)) {
      setSavedTemplates((prev) => [name, ...prev]);
    }
    setTemplateMessage(`Saved template: ${name}`);
    setTimeout(() => setTemplateMessage(''), 3000);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-bold text-slate-900 dark:text-white">
            Basket Order Manager & Trade Scaling Engine
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Multi-leg non-atomic execution reconciliation. Add-ons require fresh structure confirmation and RiskManager budget approval.
          </p>
        </div>
        {activePositions.length > 0 && (
          <button
            type="button"
            onClick={onEmergencySquareOffAll}
            data-tip-title="Emergency Square-Off All"
            data-tip="Immediately exits all open paper/live positions and engages the Emergency Kill Switch to block any new orders."
            className="min-h-[42px] px-4 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold inline-flex items-center gap-2 self-start sm:self-auto cursor-pointer"
          >
            <AlertOctagon className="w-4 h-4" />
            <span>Emergency Square-Off All ({activePositions.length})</span>
          </button>
        )}
      </div>

      {/* Inside Tab 1: Basket Order Manager */}
      {insideTab === 'builder' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          {/* Left: Basket Legs */}
          <div className="lg:col-span-8 space-y-4">
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                    Staged Basket Legs ({basketLegs.length})
                  </h2>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Each leg tracks fill state independently; partial fills and rejections are reconciled safely.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => onAddStagedLeg('Add-on 2')}
                    data-tip="Add a conditional add-on option leg to the current staged basket."
                    className="min-h-[36px] px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium inline-flex items-center gap-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" /> Add Staged Leg
                  </button>
                  <button
                    type="button"
                    onClick={handleSaveTemplate}
                    data-tip="Save the current leg configuration as a reusable basket template."
                    className="min-h-[36px] px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium inline-flex items-center gap-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                  >
                    <Save className="w-3.5 h-3.5" /> Save Template
                  </button>
                </div>
              </div>

              {templateMessage && (
                <div className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">
                  {templateMessage}
                </div>
              )}

              {basketLegs.length === 0 ? (
                <div className="py-10 text-center space-y-3 text-xs text-slate-500 dark:text-slate-400">
                  <p>No legs currently staged in your basket.</p>
                  <button
                    type="button"
                    onClick={() => onAddStagedLeg('Initial Entry')}
                    className="min-h-[40px] px-4 py-2 rounded-lg bg-sky-600 text-white font-semibold cursor-pointer"
                  >
                    Stage Initial {indices[activeIndex].symbol} Leg
                  </button>
                </div>
              ) : (
                <div className="divide-y divide-slate-200 dark:divide-slate-800">
                  {basketLegs.map((leg) => (
                    <div key={leg.id} className="py-4 first:pt-1 last:pb-1 space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          <span className="font-semibold text-sky-600 dark:text-sky-400">
                            {leg.kind}
                          </span>
                          <span aria-hidden="true">·</span>
                          <span className="font-display font-bold text-sm text-slate-900 dark:text-white">
                            {leg.tradingSymbol}
                          </span>
                          <span aria-hidden="true">·</span>
                          <span className="text-slate-500 dark:text-slate-400">
                            {leg.side} · Lot Size {leg.lotSize}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 text-xs">
                          <span
                            className={`font-semibold ${
                              leg.fillStatus === 'Filled'
                                ? 'text-emerald-600 dark:text-emerald-400'
                                : leg.fillStatus === 'Partial Fill'
                                ? 'text-amber-600 dark:text-amber-400'
                                : leg.fillStatus === 'Rejected' || leg.fillStatus === 'Cancelled'
                                ? 'text-rose-600 dark:text-rose-400'
                                : 'text-slate-500 dark:text-slate-400'
                            }`}
                          >
                            {leg.fillStatus}
                            {leg.filledLots > 0 ? ` (${leg.filledLots}/${leg.lots} lots)` : ''}
                          </span>
                          <button
                            type="button"
                            onClick={() => onRemoveLeg(leg.id)}
                            aria-label="Remove leg"
                            className="p-1.5 rounded-md text-slate-400 hover:text-rose-500 cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>

                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                        <div>
                          <label className="block text-[11px] text-slate-500 mb-1">Lots</label>
                          <input
                            type="number"
                            min={1}
                            max={20}
                            value={leg.lots}
                            onChange={(e) =>
                              onUpdateLeg(leg.id, { lots: Math.max(1, Number(e.target.value)) })
                            }
                            className="w-full min-h-[38px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono tabular-nums"
                          />
                        </div>
                        <div>
                          <label className="block text-[11px] text-slate-500 mb-1">Limit Price (₹)</label>
                          <input
                            type="number"
                            step="0.5"
                            value={leg.limitPrice}
                            onChange={(e) =>
                              onUpdateLeg(leg.id, { limitPrice: Math.max(1, Number(e.target.value)) })
                            }
                            className="w-full min-h-[38px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono tabular-nums"
                          />
                        </div>
                        <div>
                          <label className="block text-[11px] text-slate-500 mb-1">Stop-Loss (₹)</label>
                          <input
                            type="number"
                            step="0.5"
                            value={leg.stopLoss}
                            onChange={(e) =>
                              onUpdateLeg(leg.id, { stopLoss: Math.max(0.5, Number(e.target.value)) })
                            }
                            className="w-full min-h-[38px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono tabular-nums text-rose-600 dark:text-rose-400"
                          />
                        </div>
                        <div>
                          <label className="block text-[11px] text-slate-500 mb-1">Target (₹)</label>
                          <input
                            type="number"
                            step="0.5"
                            value={leg.target}
                            onChange={(e) =>
                              onUpdateLeg(leg.id, { target: Math.max(1, Number(e.target.value)) })
                            }
                            className="w-full min-h-[38px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] font-mono tabular-nums text-sky-600 dark:text-sky-400"
                          />
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-500 dark:text-slate-400">
                        <span>Condition: {leg.conditionNote}</span>
                        {leg.fillStatus === 'Pending' || leg.fillStatus === 'Validated' ? (
                          <button
                            type="button"
                            onClick={() => onSimulatePartialFill(leg.id)}
                            className="text-sky-600 dark:text-sky-400 hover:underline cursor-pointer"
                          >
                            Test Partial Fill
                          </button>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Right: RiskManager Pre-Trade Gate & Execution Controls */}
          <div className="lg:col-span-4 space-y-4">
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
                  RiskManager Basket Check
                </h3>
                {validation.approved ? (
                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 className="w-4 h-4" /> Approved
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-rose-600 dark:text-rose-400">
                    <ShieldAlert className="w-4 h-4" /> Blocked
                  </span>
                )}
              </div>

              <div className="space-y-2.5 text-xs font-mono tabular-nums border-y border-slate-200 dark:border-slate-800 py-3">
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Total Lots / Weighted Avg</span>
                  <span className="font-semibold">
                    {validation.totalLots} lots @ ₹{validation.weightedAvgPremium}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Total Capital Required</span>
                  <span className="font-semibold">
                    ₹{validation.totalCapitalRequiredInr.toLocaleString('en-IN')}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Worst-Case Planned Loss</span>
                  <span className="font-semibold text-rose-600 dark:text-rose-400">
                    ₹{validation.worstCaseLossInr.toLocaleString('en-IN')}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Remaining Risk Allowance</span>
                  <span className="font-semibold text-sky-600 dark:text-sky-400">
                    ₹{Math.max(0, validation.remainingRiskBudgetInr).toLocaleString('en-IN')}
                  </span>
                </div>
              </div>

              {!validation.approved && (
                <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 space-y-1.5 text-xs text-rose-600 dark:text-rose-300">
                  {validation.reasons.map((r) => (
                    <div key={r} className="flex items-start gap-1.5">
                      <XCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                      <span>{r}</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="space-y-2 pt-1">
                <button
                  type="button"
                  disabled={!validation.approved}
                  onClick={onSubmitBasketPaper}
                  data-tip="Simulate filling all approved basket legs together after RiskManager passes capital & risk checks."
                  className="w-full min-h-[46px] px-4 py-2.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                >
                  Execute Basket (Paper Desk)
                </button>
                <button
                  type="button"
                  onClick={onCancelPendingLegs}
                  data-tip="Cancel unfilled pending legs without disturbing already executed positions."
                  className="w-full min-h-[40px] px-4 py-2 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
                >
                  Cancel Pending / Unfilled Legs
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 space-y-2 text-xs">
              <div className="font-semibold text-slate-900 dark:text-white">Saved Basket Templates</div>
              {savedTemplates.map((t) => (
                <div key={t} className="text-slate-500 dark:text-slate-400 py-1 border-b border-slate-100 dark:border-slate-800 last:border-0">
                  {t}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 2: Position Scaling Engine */}
      {insideTab === 'scaling' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                  Controlled Add-On & Anti-Averaging Guard
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Hard Rule: Averaging down on a losing position is permanently blocked by RiskManager. Simulate LTP moves below to test!
                </p>
              </div>
              <label className="inline-flex items-center gap-2 text-xs cursor-pointer">
                <input
                  type="checkbox"
                  checked={confirmedBreakout}
                  onChange={(e) => setConfirmedBreakout(e.target.checked)}
                />
                <span>5m Fresh BOS + Volume Confirmation Satisfied</span>
              </label>
            </div>

            {activePositions.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-500">
                No active positions to scale.{' '}
                <button
                  type="button"
                  onClick={() => onChangeInsideTab('builder')}
                  className="text-sky-600 dark:text-sky-400 underline cursor-pointer"
                >
                  Open Basket Builder
                </button>
              </div>
            ) : (
              <div className="space-y-4">
                {activePositions.map((pos) => {
                  const unrealized = (pos.currentLtp - pos.avgEntryPrice) * pos.totalLots * pos.lotSize;
                  const isProfitable = pos.currentLtp > pos.avgEntryPrice;
                  return (
                    <div
                      key={pos.id}
                      className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-[#0B1120] space-y-4"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <div className="text-xs text-slate-500">
                            {pos.index} · {pos.totalLots} lot(s) ({pos.totalLots * pos.lotSize} qty) · {pos.mode}
                          </div>
                          <div className="font-display font-bold text-base text-slate-900 dark:text-white">
                            {pos.tradingSymbol}
                          </div>
                        </div>

                        <div className="text-right font-mono tabular-nums">
                          <div className="text-sm font-bold text-slate-900 dark:text-white">
                            Avg ₹{pos.avgEntryPrice.toFixed(2)} → LTP ₹{pos.currentLtp.toFixed(2)}
                          </div>
                          <div
                            className={`text-xs font-semibold ${
                              unrealized >= 0
                                ? 'text-sky-600 dark:text-sky-400'
                                : 'text-rose-600 dark:text-rose-400'
                            }`}
                          >
                            Unrealized: {unrealized >= 0 ? '+' : ''}₹{unrealized.toLocaleString('en-IN')}
                          </div>
                        </div>
                      </div>

                      {/* Entry Stage History */}
                      <div className="overflow-x-auto">
                        <table className="w-full text-xs font-mono tabular-nums">
                          <thead>
                            <tr className="text-left text-slate-500 border-b border-slate-200 dark:border-slate-800">
                              <th className="py-1.5 font-sans">Stage</th>
                              <th className="py-1.5 text-right">Lots</th>
                              <th className="py-1.5 text-right">Fill Price</th>
                              <th className="py-1.5 text-right">Timestamp</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                            {pos.entryStages.map((st, i) => (
                              <tr key={i}>
                                <td className="py-1.5 font-sans">{st.stage}</td>
                                <td className="py-1.5 text-right">{st.lots}</td>
                                <td className="py-1.5 text-right">₹{st.price.toFixed(2)}</td>
                                <td className="py-1.5 text-right">{st.timestamp}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      {/* Interactive Scale-In & Market Tick Simulator */}
                      <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
                        <div className="flex items-center gap-2 text-xs">
                          <span className="text-slate-500">Simulate Tick:</span>
                          <button
                            type="button"
                            onClick={() => onAdjustPositionLtp(pos.id, 15)}
                            data-tip="Simulate +₹15 premium move to test profit scaling & trailing SL behavior."
                            className="min-h-[34px] px-2.5 rounded-md border border-sky-500/40 text-sky-600 dark:text-sky-400 font-mono cursor-pointer"
                          >
                            +₹15
                          </button>
                          <button
                            type="button"
                            onClick={() => onAdjustPositionLtp(pos.id, -30)}
                            data-tip="Simulate -₹30 drawdown move to verify RiskManager blocks averaging down on losing positions."
                            className="min-h-[34px] px-2.5 rounded-md border border-rose-500/40 text-rose-600 dark:text-rose-400 font-mono cursor-pointer"
                          >
                            -₹30
                          </button>
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            type="button"
                            onClick={() => onScaleInPosition(pos.id, 1, confirmedBreakout)}
                            data-tip="Adds +1 lot only if position is profitable (LTP > Avg) and 5m BOS confirmation is satisfied."
                            className={`min-h-[38px] px-3.5 py-1.5 rounded-lg text-xs font-semibold inline-flex items-center gap-1.5 cursor-pointer ${
                              isProfitable && confirmedBreakout
                                ? 'bg-sky-600 hover:bg-sky-500 text-white'
                                : 'bg-rose-600/15 text-rose-600 dark:text-rose-300 border border-rose-500/40'
                            }`}
                          >
                            <TrendingUp className="w-3.5 h-3.5" />
                            <span>
                              {isProfitable
                                ? 'Scale-In +1 Confirmed Lot'
                                : 'Attempt Add-On (Triggers Risk Veto)'}
                            </span>
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Inside Tab 3: Active Positions & Exits */}
      {insideTab === 'positions' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
              Open Positions & Partial Exit Controls
            </h2>
            <span className="text-xs text-slate-500">Paper Trading Desk</span>
          </div>

          {activePositions.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">
              No open positions right now.
            </div>
          ) : (
            <div className="divide-y divide-slate-200 dark:divide-slate-800">
              {activePositions.map((pos) => {
                const pnl = (pos.currentLtp - pos.avgEntryPrice) * pos.totalLots * pos.lotSize;
                return (
                  <div
                    key={pos.id}
                    className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
                  >
                    <div className="space-y-1">
                      <div className="text-xs text-slate-500 dark:text-slate-400">
                        {pos.index} · {pos.expiry} · Trailing: {pos.trailingMethod}
                      </div>
                      <div className="font-display font-bold text-base text-slate-900 dark:text-white">
                        {pos.tradingSymbol} · {pos.totalLots} Lot(s)
                      </div>
                      <div className="text-xs font-mono tabular-nums text-slate-500 dark:text-slate-400">
                        Avg ₹{pos.avgEntryPrice.toFixed(2)} · LTP ₹{pos.currentLtp.toFixed(2)} · SL ₹{pos.stopLoss} · T1 ₹{pos.target1}
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-3">
                      <div
                        className={`font-mono text-sm font-bold tabular-nums ${
                          pnl >= 0
                            ? 'text-sky-600 dark:text-sky-400'
                            : 'text-rose-600 dark:text-rose-400'
                        }`}
                      >
                        {pnl >= 0 ? '+' : ''}₹{pnl.toLocaleString('en-IN')}
                      </div>
                      {pos.totalLots > 1 && (
                        <button
                          type="button"
                          onClick={() => onPartialExitPosition(pos.id, 1)}
                          data-tip="Close 1 lot at current LTP, deduct charges, and release principal back to your strategy allocation."
                          className="min-h-[38px] px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium cursor-pointer"
                        >
                          Partial Exit 1 Lot
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => onPartialExitPosition(pos.id, pos.totalLots)}
                        data-tip="Close all remaining lots at current LTP, log realized net P&L, and unlock strategy capital."
                        className="min-h-[38px] px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold cursor-pointer"
                      >
                        Full Exit
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
