import React, { useState } from 'react';
import {
  ArrowUpRight,
  ArrowDownRight,
  Bookmark,
  Layers,
  ShieldCheck,
  AlertTriangle,
  SlidersHorizontal,
  CheckCircle2,
  X,
} from 'lucide-react';
import {
  CapitalLedger,
  DashboardInsideTab,
  DecisionStatus,
  IndexMasterSpec,
  IndexSymbol,
  RiskLimits,
  SectorMetric,
  TradeOpportunity,
} from '../../types/pie';
import { calculateOpportunityFinancials } from '../../engine/pieEngine';
import { PriceStructureChart } from '../charts/IndexCharts';

interface DashboardViewProps {
  insideTab: DashboardInsideTab;
  onChangeInsideTab: (tab: DashboardInsideTab) => void;
  activeIndex: IndexSymbol;
  onSelectIndex: (index: IndexSymbol) => void;
  indices: Record<IndexSymbol, IndexMasterSpec>;
  opportunities: TradeOpportunity[];
  sectors: SectorMetric[];
  ledger: CapitalLedger;
  limits: RiskLimits;
  watchlistIds: string[];
  onToggleWatchlist: (id: string) => void;
  onSendToBasket: (opp: TradeOpportunity) => void;
  onPaperExecute: (opp: TradeOpportunity) => void;
  onInspectPipeline: (index: IndexSymbol) => void;
}

function decisionStyle(decision: DecisionStatus): string {
  switch (decision) {
    case 'STRONG OPPORTUNITY':
      return 'text-emerald-600 dark:text-emerald-400';
    case 'VALID OPPORTUNITY':
      return 'text-sky-600 dark:text-sky-400';
    case 'WAIT FOR CONFIRMATION':
      return 'text-amber-600 dark:text-amber-400';
    case 'NO TRADE':
      return 'text-slate-500 dark:text-slate-400';
    case 'RISK BLOCKED':
      return 'text-rose-600 dark:text-rose-400';
  }
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  insideTab,
  onChangeInsideTab,
  activeIndex,
  onSelectIndex,
  indices,
  opportunities,
  sectors,
  ledger,
  limits,
  watchlistIds,
  onToggleWatchlist,
  onSendToBasket,
  onPaperExecute,
  onInspectPipeline,
}) => {
  const [discoveryMode, setDiscoveryMode] = useState<'PIE_ALL' | 'SELECTED_INDEX'>('PIE_ALL');
  const [compareIds, setCompareIds] = useState<string[]>([]);
  const [detailOpp, setDetailOpp] = useState<TradeOpportunity | null>(null);

  const currentSpec = indices[activeIndex];
  const displayedOpportunities =
    discoveryMode === 'PIE_ALL'
      ? [...opportunities].sort((a, b) => a.suitabilityRank - b.suitabilityRank)
      : opportunities.filter((o) => o.index === activeIndex);

  const toggleCompare = (id: string) => {
    setCompareIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev.slice(-1), id]
    );
  };

  const comparedItems = opportunities.filter((o) => compareIds.includes(o.id));

  return (
    <div className="space-y-6">
      {/* Top Discovery Mode & Summary Strip */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-xl sm:text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Market Intelligence & Opportunity Desk
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-0.5">
            {discoveryMode === 'PIE_ALL'
              ? 'PIE All-Market Rank evaluates suitability, probability, liquidity, and net risk-adjusted expectancy.'
              : `Focused analysis on ${activeIndex} (${currentSpec.exchange}) · ${currentSpec.regime}.`}
          </p>
        </div>

        {/* Choice A / B Segmented Discovery Toggle */}
        <div className="inline-flex p-1 rounded-lg bg-slate-200/70 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setDiscoveryMode('PIE_ALL')}
            data-tip-title="All-Market Rank"
            data-tip="Ranks setups across all 5 supported Indian indices by evidence score, liquidity, and net expectancy."
            className={`min-h-[36px] px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
              discoveryMode === 'PIE_ALL'
                ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400'
            }`}
          >
            PIE All-Market Rank
          </button>
          <button
            type="button"
            onClick={() => setDiscoveryMode('SELECTED_INDEX')}
            data-tip-title="Focused Index Filter"
            data-tip={`Show opportunities exclusively for ${activeIndex}.`}
            className={`min-h-[36px] px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
              discoveryMode === 'SELECTED_INDEX'
                ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400'
            }`}
          >
            Selected: {activeIndex}
          </button>
        </div>
      </div>

      {/* Global & Indian Market Overview Strip (Clean hairline grid, no nested cards) */}
      <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] grid grid-cols-2 md:grid-cols-5 divide-y md:divide-y-0 md:divide-x divide-slate-200 dark:divide-slate-800">
        {(Object.keys(indices) as IndexSymbol[]).map((sym) => {
          const spec = indices[sym];
          const isSelected = sym === activeIndex;
          return (
            <button
              key={sym}
              type="button"
              onClick={() => onSelectIndex(sym)}
              data-tip-title={`${sym} (${spec.exchange})`}
              data-tip={`Select ${sym} as the shared active index across Option Chain, Intelligence & Basket.`}
              className={`p-3.5 sm:p-4 text-left transition-colors cursor-pointer ${
                isSelected ? 'bg-sky-500/8' : 'hover:bg-slate-50 dark:hover:bg-slate-800/40'
              }`}
            >
              <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span className={isSelected ? 'font-semibold text-sky-600 dark:text-sky-400' : ''}>
                  {sym}
                </span>
                <span className="font-mono tabular-nums">PCR {spec.pcr.toFixed(2)}</span>
              </div>
              <div className="mt-1 flex items-baseline justify-between gap-2">
                <span className="font-mono text-base font-semibold tabular-nums text-slate-900 dark:text-white">
                  {spec.spot.toLocaleString('en-IN')}
                </span>
                <span
                  className={`font-mono text-xs tabular-nums flex items-center ${
                    spec.dayChange >= 0
                      ? 'text-sky-600 dark:text-sky-400'
                      : 'text-rose-600 dark:text-rose-400'
                  }`}
                >
                  {spec.dayChange >= 0 ? (
                    <ArrowUpRight className="w-3.5 h-3.5" />
                  ) : (
                    <ArrowDownRight className="w-3.5 h-3.5" />
                  )}
                  {Math.abs(spec.dayChangePct).toFixed(2)}%
                </span>
              </div>
              <div className="mt-1 text-[11px] text-slate-500 dark:text-slate-400 truncate">
                {spec.regime} · Lot {spec.lotSize}
              </div>
            </button>
          );
        })}
      </div>

      {/* Inside Tab 1: PIE Ranked Opportunities */}
      {insideTab === 'ranked' && (
        <div className="space-y-5">
          {comparedItems.length === 2 && (
            <div className="rounded-xl border border-sky-500/40 bg-white dark:bg-[#111827] p-4 space-y-3">
              <div className="flex items-center justify-between">
                <h2 className="font-display font-semibold text-sm text-slate-900 dark:text-white">
                  Side-by-Side Opportunity Comparison
                </h2>
                <button
                  type="button"
                  onClick={() => setCompareIds([])}
                  className="text-xs text-slate-500 hover:text-slate-800 dark:hover:text-white cursor-pointer"
                >
                  Clear Compare
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-xs font-mono tabular-nums">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 text-left">
                      <th className="py-2 pr-4 font-sans">Metric</th>
                      {comparedItems.map((c) => (
                        <th key={c.id} className="py-2 pr-4 text-slate-900 dark:text-white">
                          {c.contract.tradingSymbol}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    <tr>
                      <td className="py-2 pr-4 font-sans text-slate-500">Decision</td>
                      {comparedItems.map((c) => (
                        <td key={c.id} className={`py-2 pr-4 font-semibold ${decisionStyle(c.decision)}`}>
                          {c.decision}
                        </td>
                      ))}
                    </tr>
                    <tr>
                      <td className="py-2 pr-4 font-sans text-slate-500">P(Target 1) / Sample n</td>
                      {comparedItems.map((c) => (
                        <td key={c.id} className="py-2 pr-4">
                          {c.probability.sufficientEvidence
                            ? `${c.probability.probTarget1}% (n=${c.probability.sampleSize})`
                            : 'INSUFFICIENT EVIDENCE'}
                        </td>
                      ))}
                    </tr>
                    <tr>
                      <td className="py-2 pr-4 font-sans text-slate-500">Net Expected Value</td>
                      {comparedItems.map((c) => (
                        <td key={c.id} className="py-2 pr-4 text-sky-600 dark:text-sky-400">
                          ₹{c.probability.expectedValueInr.toLocaleString('en-IN')}
                        </td>
                      ))}
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Trade Cards Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {displayedOpportunities.map((opp) => {
              const fin = calculateOpportunityFinancials(opp, ledger, limits);
              const isWatchlisted = watchlistIds.includes(opp.id);
              const isCompared = compareIds.includes(opp.id);
              const blockedOrNoTrade = opp.decision === 'NO TRADE' || opp.decision === 'RISK BLOCKED';

              return (
                <article
                  key={opp.id}
                  className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 flex flex-col justify-between gap-4"
                >
                  {/* Card Header: Priority 1-3 (Market, Direction, Contract) + Clean Metadata */}
                  <div className="space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                          <span>Rank #{opp.suitabilityRank}</span>
                          <span aria-hidden="true">·</span>
                          <span>{opp.index}</span>
                          <span aria-hidden="true">·</span>
                          <span>{opp.direction}</span>
                          <span aria-hidden="true">·</span>
                          <span>{opp.regime}</span>
                        </div>
                        <h3 className="font-display text-base sm:text-lg font-bold text-slate-900 dark:text-white mt-0.5 truncate">
                          {opp.contract.tradingSymbol}
                        </h3>
                      </div>

                      <div className="text-right shrink-0">
                        <div className={`text-xs font-bold tracking-wide ${decisionStyle(opp.decision)}`}>
                          {opp.decision}
                        </div>
                        <div className="text-[11px] font-mono tabular-nums text-slate-500 dark:text-slate-400 mt-0.5">
                          LTP ₹{opp.contract.ltp.toFixed(2)} · Δ {opp.contract.delta}
                        </div>
                      </div>
                    </div>

                    <p className="text-xs text-slate-600 dark:text-slate-300">
                      {opp.setupName}
                    </p>
                  </div>

                  {/* Priority 4-9: Key Plan & Risk-Adjusted Numbers (Tabular Monospace) */}
                  <div className="grid grid-cols-3 sm:grid-cols-6 gap-3 py-3 border-y border-slate-200 dark:border-slate-800 text-xs">
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">Entry</div>
                      <div className="font-mono font-semibold tabular-nums text-slate-900 dark:text-white">
                        ₹{opp.tradePlan.entryRange[0]}–{opp.tradePlan.entryRange[1]}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">Stop-Loss</div>
                      <div className="font-mono font-semibold tabular-nums text-rose-600 dark:text-rose-400">
                        ₹{opp.tradePlan.initialStopLoss}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">T1 / T2</div>
                      <div className="font-mono font-semibold tabular-nums text-sky-600 dark:text-sky-400">
                        {opp.tradePlan.target1} / {opp.tradePlan.target2}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">Net R:R</div>
                      <div className="font-mono font-semibold tabular-nums text-slate-900 dark:text-white">
                        1 : {fin.netRiskReward}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">P(T1) · n</div>
                      <div className="font-mono font-semibold tabular-nums text-slate-900 dark:text-white">
                        {opp.probability.sufficientEvidence
                          ? `${opp.probability.probTarget1}% (${opp.probability.sampleSize})`
                          : `n=${opp.probability.sampleSize} Low`}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-slate-500 dark:text-slate-400">Capital</div>
                      <div className="font-mono font-semibold tabular-nums text-slate-900 dark:text-white">
                        ₹{fin.allocatedCapitalInr.toLocaleString('en-IN')}
                      </div>
                    </div>
                  </div>

                  {/* Net Profit vs Fees Transparency + Insufficient Evidence Warning */}
                  <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-500 dark:text-slate-400 font-mono tabular-nums">
                    {opp.probability.sufficientEvidence ? (
                      <span>
                        Net Profit Est: ₹{fin.potentialNetProfitInr.toLocaleString('en-IN')} (after ₹
                        {(fin.estimatedFeesAndTaxesInr + fin.estimatedSlippageInr).toFixed(0)} fees & slippage)
                      </span>
                    ) : (
                      <span className="text-amber-600 dark:text-amber-400 font-sans font-semibold flex items-center gap-1">
                        <AlertTriangle className="w-3.5 h-3.5" />
                        INSUFFICIENT HISTORICAL EVIDENCE (n={opp.probability.sampleSize} &lt; 30)
                      </span>
                    )}
                    <span>
                      Liq {opp.contract.liquidityScore}/100 · {opp.validUntil}
                    </span>
                  </div>

                  {/* Card Actions (Ergonomic mobile touch heights) */}
                  <div className="flex flex-wrap items-center gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setDetailOpp(opp)}
                      data-tip="Open complete 7-dimension breakdown, entry trigger, stop-loss, targets, and invalidation rules."
                      className="min-h-[40px] px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                    >
                      Full Analysis
                    </button>
                    <button
                      type="button"
                      onClick={() => toggleCompare(opp.id)}
                      data-tip="Pin up to 2 setups side-by-side to compare probability, net expectancy, and liquidity."
                      className={`min-h-[40px] px-3 py-1.5 rounded-lg border text-xs font-medium transition-colors cursor-pointer ${
                        isCompared
                          ? 'border-sky-500 bg-sky-500/15 text-sky-600 dark:text-sky-300'
                          : 'border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
                      }`}
                    >
                      {isCompared ? 'Comparing' : 'Compare'}
                    </button>
                    <button
                      type="button"
                      onClick={() => onToggleWatchlist(opp.id)}
                      aria-label="Toggle watchlist"
                      data-tip="Save or remove this contract from your session watchlist."
                      className={`min-h-[40px] px-2.5 py-1.5 rounded-lg border text-xs transition-colors cursor-pointer ${
                        isWatchlisted
                          ? 'border-amber-500/60 bg-amber-500/10 text-amber-600 dark:text-amber-400'
                          : 'border-slate-200 dark:border-slate-700 text-slate-500 hover:text-slate-800 dark:hover:text-white'
                      }`}
                    >
                      <Bookmark className="w-3.5 h-3.5" />
                    </button>

                    <div className="flex-1" />

                    <button
                      type="button"
                      disabled={blockedOrNoTrade}
                      onClick={() => onSendToBasket(opp)}
                      data-tip="Stage initial entry + conditional add-on leg inside Basket Builder for RiskManager pre-check."
                      className="min-h-[40px] px-3 py-1.5 rounded-lg border border-sky-500/40 text-sky-600 dark:text-sky-400 text-xs font-semibold hover:bg-sky-500/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                    >
                      Build Basket
                    </button>
                    <button
                      type="button"
                      disabled={blockedOrNoTrade}
                      onClick={() => onPaperExecute(opp)}
                      data-tip="Simulate immediate paper fill for the initial lot size after RiskManager verification."
                      className="min-h-[40px] px-3.5 py-1.5 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                    >
                      Paper Trade
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}

      {/* Inside Tab 2: Index Focus & Chart */}
      {insideTab === 'index-focus' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <div className="lg:col-span-8 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                  {currentSpec.symbol} Intraday Structure & ICT Levels
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {currentSpec.smcStructure.activeSignal}
                </p>
              </div>
              <button
                type="button"
                onClick={() => onInspectPipeline(activeIndex)}
                data-tip={`Jump to the 10-stage 7D Decision Pipeline audit for ${activeIndex}.`}
                className="min-h-[36px] px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              >
                Inspect 7D Pipeline
              </button>
            </div>

            <PriceStructureChart
              candles={currentSpec.candles}
              pdh={currentSpec.pdh}
              pdl={currentSpec.pdl}
              fvgZone={currentSpec.smcStructure.fvgZone}
              symbol={currentSpec.symbol}
            />
          </div>

          <div className="lg:col-span-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
            <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
              Multi-Timeframe Alignment
            </h3>
            <div className="divide-y divide-slate-200 dark:divide-slate-800 text-xs">
              {[
                { tf: 'Daily (Macro Structure)', val: currentSpec.smcStructure.daily },
                { tf: '4-Hour (Major Structure)', val: currentSpec.smcStructure.h4 },
                { tf: '1-Hour (Directional Context)', val: currentSpec.smcStructure.h1 },
                { tf: '15-Minute (Setup)', val: currentSpec.smcStructure.m15 },
                { tf: '5-Minute (Confirmation)', val: currentSpec.smcStructure.m5 },
                { tf: '3-Minute (Execution)', val: currentSpec.smcStructure.m3 },
              ].map((row) => (
                <div key={row.tf} className="py-2.5 flex items-center justify-between">
                  <span className="text-slate-600 dark:text-slate-400">{row.tf}</span>
                  <span
                    className={`font-semibold ${
                      row.val === 'Bullish'
                        ? 'text-sky-600 dark:text-sky-400'
                        : row.val === 'Bearish'
                        ? 'text-rose-600 dark:text-rose-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {row.val}
                  </span>
                </div>
              ))}
            </div>

            <div className="pt-2 border-t border-slate-200 dark:border-slate-800 space-y-2 text-xs font-mono tabular-nums">
              <div className="flex justify-between">
                <span className="font-sans text-slate-500">Order Block Zone</span>
                <span>
                  {currentSpec.smcStructure.orderBlockZone[0]}–{currentSpec.smcStructure.orderBlockZone[1]}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="font-sans text-slate-500">Buy-Side Liquidity Target</span>
                <span>{currentSpec.smcStructure.liquidityTarget}</span>
              </div>
              <div className="flex justify-between">
                <span className="font-sans text-slate-500">ATR (14) / Max Pain</span>
                <span>
                  {currentSpec.atr14} / {currentSpec.maxPain}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 3: Sector Rotation */}
      {insideTab === 'sectors' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <div>
              <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                Sector Rotation & Relative Strength Matrix
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Tracks institutional FII leadership and breadth across NSE sectoral indices.
              </p>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 text-left">
                  <th className="py-3 px-5 font-medium">Sector Index</th>
                  <th className="py-3 px-4 font-medium text-right">Day Chg</th>
                  <th className="py-3 px-4 font-medium text-right">RS vs NIFTY</th>
                  <th className="py-3 px-4 font-medium text-right">Breadth</th>
                  <th className="py-3 px-4 font-medium">FII Flow</th>
                  <th className="py-3 px-5 font-medium text-right">Rotation Quadrant</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {sectors.map((s) => (
                  <tr key={s.sector} className="hover:bg-slate-50 dark:hover:bg-slate-800/40">
                    <td className="py-3 px-5 font-medium text-slate-900 dark:text-white whitespace-nowrap">
                      {s.sector}
                    </td>
                    <td
                      className={`py-3 px-4 text-right font-mono tabular-nums ${
                        s.changePct >= 0
                          ? 'text-sky-600 dark:text-sky-400'
                          : 'text-rose-600 dark:text-rose-400'
                      }`}
                    >
                      {s.changePct >= 0 ? '+' : ''}
                      {s.changePct.toFixed(2)}%
                    </td>
                    <td className="py-3 px-4 text-right font-mono tabular-nums">
                      {s.relStrengthVsNifty.toFixed(2)}
                    </td>
                    <td className="py-3 px-4 text-right font-mono tabular-nums">{s.breadthPct}%</td>
                    <td className="py-3 px-4 text-slate-600 dark:text-slate-300">{s.fiiFlowBias}</td>
                    <td className="py-3 px-5 text-right font-semibold">
                      <span
                        className={
                          s.quadrant === 'Leading'
                            ? 'text-sky-600 dark:text-sky-400'
                            : s.quadrant === 'Improving'
                            ? 'text-sky-600 dark:text-sky-400'
                            : s.quadrant === 'Weakening'
                            ? 'text-amber-600 dark:text-amber-400'
                            : 'text-rose-600 dark:text-rose-400'
                        }
                      >
                        {s.quadrant}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Inside Tab 4: Market Regime Comparison */}
      {insideTab === 'regime' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800">
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
              Cross-Index Regime, Liquidity & Contract Master
            </h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 text-left">
                  <th className="py-3 px-5 font-medium">Index</th>
                  <th className="py-3 px-4 font-medium">Regime</th>
                  <th className="py-3 px-4 font-medium text-right">Spot</th>
                  <th className="py-3 px-4 font-medium text-right">VWAP</th>
                  <th className="py-3 px-4 font-medium text-right">PCR</th>
                  <th className="py-3 px-4 font-medium text-right">Lot / Step</th>
                  <th className="py-3 px-5 font-medium text-right">Adv / Dec</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {(Object.keys(indices) as IndexSymbol[]).map((sym) => {
                  const s = indices[sym];
                  return (
                    <tr
                      key={sym}
                      onClick={() => {
                        onSelectIndex(sym);
                        onChangeInsideTab('index-focus');
                      }}
                      className="hover:bg-slate-50 dark:hover:bg-slate-800/40 cursor-pointer"
                    >
                      <td className="py-3 px-5 font-semibold text-slate-900 dark:text-white whitespace-nowrap">
                        {s.symbol} · {s.exchange}
                      </td>
                      <td className="py-3 px-4 text-slate-600 dark:text-slate-300 whitespace-nowrap">
                        {s.regime}
                      </td>
                      <td className="py-3 px-4 text-right font-mono tabular-nums">
                        {s.spot.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3 px-4 text-right font-mono tabular-nums">
                        {s.vwap.toLocaleString('en-IN')}
                      </td>
                      <td className="py-3 px-4 text-right font-mono tabular-nums">{s.pcr.toFixed(2)}</td>
                      <td className="py-3 px-4 text-right font-mono tabular-nums">
                        {s.lotSize} / {s.strikeInterval}
                      </td>
                      <td className="py-3 px-5 text-right font-mono tabular-nums">
                        {s.advances} / {s.declines}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Full Analysis Modal / Drawer */}
      {detailOpp && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-0 sm:p-4"
          onClick={() => setDetailOpp(null)}
        >
          <div
            className="w-full max-w-2xl rounded-t-3xl sm:rounded-2xl bg-white dark:bg-[#111827] border border-slate-200 dark:border-slate-800 p-5 sm:p-6 max-h-[88vh] overflow-y-auto space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="text-xs text-slate-500 dark:text-slate-400">
                  {detailOpp.index} · {detailOpp.regime} · Evidence Score {detailOpp.evidenceScore}/100
                </div>
                <h2 className="font-display text-lg font-bold text-slate-900 dark:text-white mt-0.5">
                  {detailOpp.contract.tradingSymbol} — {detailOpp.decision}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setDetailOpp(null)}
                className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
                <div className="text-slate-500 mb-1 flex items-center gap-1.5 font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> Positive Evidence
                </div>
                <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                  {detailOpp.positiveEvidence.map((item) => (
                    <li key={item}>• {item}</li>
                  ))}
                </ul>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
                <div className="text-slate-500 mb-1 flex items-center gap-1.5 font-semibold">
                  <AlertTriangle className="w-3.5 h-3.5 text-rose-500" /> Negative Evidence
                </div>
                <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                  {detailOpp.negativeEvidence.map((item) => (
                    <li key={item}>• {item}</li>
                  ))}
                </ul>
              </div>
              <div className="p-3 rounded-lg bg-slate-50 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
                <div className="text-slate-500 mb-1 flex items-center gap-1.5 font-semibold">
                  <SlidersHorizontal className="w-3.5 h-3.5 text-amber-500" /> Missing / Risk
                </div>
                <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                  {detailOpp.missingEvidence.map((item) => (
                    <li key={item}>• {item}</li>
                  ))}
                  <li className="text-rose-600 dark:text-rose-400 font-medium">
                    Invalidation: {detailOpp.tradePlan.invalidationCondition}
                  </li>
                </ul>
              </div>
            </div>

            <div className="text-xs font-mono tabular-nums grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 border-t border-slate-200 dark:border-slate-800">
              <div>
                <div className="font-sans text-slate-500">Greeks (Δ / Γ)</div>
                <div>
                  {detailOpp.contract.delta} / {detailOpp.contract.gamma}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Theta / Vega</div>
                <div>
                  {detailOpp.contract.theta} / {detailOpp.contract.vega}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Bid / Ask</div>
                <div>
                  ₹{detailOpp.contract.bid} / ₹{detailOpp.contract.ask}
                </div>
              </div>
              <div>
                <div className="font-sans text-slate-500">Hold Window</div>
                <div className="font-sans">{detailOpp.tradePlan.expectedHoldingTime}</div>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-end gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => {
                  onInspectPipeline(detailOpp.index);
                  setDetailOpp(null);
                }}
                className="min-h-[42px] px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700 text-xs font-medium cursor-pointer"
              >
                Open 7D Pipeline
              </button>
              <button
                type="button"
                disabled={detailOpp.decision === 'NO TRADE' || detailOpp.decision === 'RISK BLOCKED'}
                onClick={() => {
                  onSendToBasket(detailOpp);
                  setDetailOpp(null);
                }}
                className="min-h-[42px] px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold disabled:opacity-40 cursor-pointer flex items-center gap-1.5"
              >
                <Layers className="w-3.5 h-3.5" />
                <span>Stage in Basket Builder</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
