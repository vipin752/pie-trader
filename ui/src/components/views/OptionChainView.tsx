import React, { useMemo, useState } from 'react';
import { Plus, ShieldCheck, AlertCircle } from 'lucide-react';
import {
  IndexMasterSpec,
  IndexSymbol,
  OptionChainRow,
  OptionQuoteSide,
  OptionsInsideTab,
} from '../../types/pie';
import { generateOptionChain } from '../../engine/pieEngine';
import { OptionOiProfileChart } from '../charts/IndexCharts';
import { IvSurface3DChart } from '../charts/IvSurface3DChart';

interface OptionChainViewProps {
  insideTab: OptionsInsideTab;
  activeIndex: IndexSymbol;
  onSelectIndex: (index: IndexSymbol) => void;
  indices: Record<IndexSymbol, IndexMasterSpec>;
  onAddOptionLegToBasket: (params: {
    index: IndexSymbol;
    strike: number;
    optionType: 'CE' | 'PE';
    expiry: string;
    quote: OptionQuoteSide;
    lotSize: number;
  }) => void;
}

export const OptionChainView: React.FC<OptionChainViewProps> = ({
  insideTab,
  activeIndex,
  onSelectIndex,
  indices,
  onAddOptionLegToBasket,
}) => {
  const [expiryIdx, setExpiryIdx] = useState(0);
  const spec = indices[activeIndex];
  const safeExpiryIdx = Math.min(expiryIdx, spec.expiries.length - 1);
  const selectedExpiry = spec.expiries[safeExpiryIdx];

  const rows: OptionChainRow[] = useMemo(
    () => generateOptionChain(spec, safeExpiryIdx),
    [spec, safeExpiryIdx]
  );

  const totalCeOi = rows.reduce((a, r) => a + r.ce.oi, 0);
  const totalPeOi = rows.reduce((a, r) => a + r.pe.oi, 0);
  const chainPcr = (totalPeOi / Math.max(1, totalCeOi)).toFixed(2);

  return (
    <div className="space-y-5">
      {/* Header & Shared Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-xl font-bold text-slate-900 dark:text-white">
            {spec.symbol} Options Chain & Greeks Intelligence
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 font-mono tabular-nums">
            Spot {spec.spot.toLocaleString('en-IN')} · Max Pain {spec.maxPain} · Chain PCR {chainPcr} · Lot {spec.lotSize}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Index Quick Switch */}
          <div className="inline-flex p-1 rounded-lg bg-slate-200/70 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 overflow-x-auto no-scrollbar max-w-full">
            {(['NIFTY 50', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY', 'SENSEX'] as IndexSymbol[]).map(
              (sym) => (
                <button
                  key={sym}
                  type="button"
                  onClick={() => {
                    onSelectIndex(sym);
                    setExpiryIdx(0);
                  }}
                  data-tip={`Switch active Option Chain & 3D IV Surface to ${sym}.`}
                  className={`min-h-[34px] px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer ${
                    sym === activeIndex
                      ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                      : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  {sym}
                </button>
              )
            )}
          </div>

          {/* Expiry Selector */}
          <div className="inline-flex p-1 rounded-lg bg-slate-200/70 dark:bg-slate-900 border border-slate-200 dark:border-slate-800">
            {spec.expiries.map((exp, idx) => (
              <button
                key={exp}
                type="button"
                onClick={() => setExpiryIdx(idx)}
                data-tip={`Load strike chain, OI walls, and Greeks for ${exp} expiry.`}
                className={`min-h-[34px] px-2.5 py-1 rounded-md text-xs font-mono whitespace-nowrap shrink-0 cursor-pointer ${
                  idx === safeExpiryIdx
                    ? 'bg-sky-600 text-white'
                    : 'text-slate-600 dark:text-slate-400'
                }`}
              >
                {exp}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Inside Tab 1: Option Chain Matrix */}
      {insideTab === 'chain' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
            <span>Tap CE or PE row action to stage contract directly into Basket Builder</span>
            <span className="hidden sm:inline font-mono">ATM Highlighted · Spread Check &le; 2.0%</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-xs font-mono tabular-nums">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
                  <th className="py-2.5 px-3 text-left font-sans">Stage</th>
                  <th className="py-2.5 px-2 text-right">CE OI</th>
                  <th className="py-2.5 px-2 text-right hidden md:table-cell">CE IV</th>
                  <th className="py-2.5 px-2 text-right hidden sm:table-cell">Δ</th>
                  <th className="py-2.5 px-2 text-right">CE LTP</th>
                  <th className="py-2.5 px-3 text-center bg-slate-100/70 dark:bg-slate-800/70 text-slate-900 dark:text-white font-sans">
                    Strike
                  </th>
                  <th className="py-2.5 px-2 text-right">PE LTP</th>
                  <th className="py-2.5 px-2 text-right hidden sm:table-cell">Δ</th>
                  <th className="py-2.5 px-2 text-right hidden md:table-cell">PE IV</th>
                  <th className="py-2.5 px-2 text-right">PE OI</th>
                  <th className="py-2.5 px-3 text-right font-sans">Stage</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {rows.map((row) => {
                  const ceItm = row.strike < spec.spot;
                  const peItm = row.strike > spec.spot;
                  return (
                    <tr
                      key={row.strike}
                      className={
                        row.isAtm
                          ? 'bg-sky-500/10'
                          : 'hover:bg-slate-50 dark:hover:bg-slate-800/40'
                      }
                    >
                      <td className={`py-2 px-3 ${ceItm ? 'bg-emerald-500/5' : ''}`}>
                        <button
                          type="button"
                          onClick={() =>
                            onAddOptionLegToBasket({
                              index: activeIndex,
                              strike: row.strike,
                              optionType: 'CE',
                              expiry: selectedExpiry,
                              quote: row.ce,
                              lotSize: spec.lotSize,
                            })
                          }
                          data-tip={`Stage ${row.strike} CE (${selectedExpiry}) into Basket Builder with spread & liquidity metadata.`}
                          className="min-h-[32px] px-2 rounded-md border border-emerald-500/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10 inline-flex items-center gap-1 font-sans text-[11px] font-semibold cursor-pointer"
                        >
                          <Plus className="w-3 h-3" /> CE
                        </button>
                      </td>
                      <td className={`py-2 px-2 text-right ${ceItm ? 'bg-emerald-500/5' : ''}`}>
                        {(row.ce.oi / 100000).toFixed(1)}L
                      </td>
                      <td className={`py-2 px-2 text-right hidden md:table-cell ${ceItm ? 'bg-emerald-500/5' : ''}`}>
                        {row.ce.iv}%
                      </td>
                      <td className={`py-2 px-2 text-right hidden sm:table-cell ${ceItm ? 'bg-emerald-500/5' : ''}`}>
                        {row.ce.delta.toFixed(2)}
                      </td>
                      <td className={`py-2 px-2 text-right font-semibold text-slate-900 dark:text-white ${ceItm ? 'bg-emerald-500/5' : ''}`}>
                        ₹{row.ce.ltp.toFixed(1)}
                      </td>

                      <td className="py-2 px-3 text-center font-bold bg-slate-100/70 dark:bg-slate-800/70 text-slate-900 dark:text-white">
                        {row.strike}
                      </td>

                      <td className={`py-2 px-2 text-right font-semibold text-slate-900 dark:text-white ${peItm ? 'bg-rose-500/5' : ''}`}>
                        ₹{row.pe.ltp.toFixed(1)}
                      </td>
                      <td className={`py-2 px-2 text-right hidden sm:table-cell ${peItm ? 'bg-rose-500/5' : ''}`}>
                        {row.pe.delta.toFixed(2)}
                      </td>
                      <td className={`py-2 px-2 text-right hidden md:table-cell ${peItm ? 'bg-rose-500/5' : ''}`}>
                        {row.pe.iv}%
                      </td>
                      <td className={`py-2 px-2 text-right ${peItm ? 'bg-rose-500/5' : ''}`}>
                        {(row.pe.oi / 100000).toFixed(1)}L
                      </td>
                      <td className={`py-2 px-3 text-right ${peItm ? 'bg-rose-500/5' : ''}`}>
                        <button
                          type="button"
                          onClick={() =>
                            onAddOptionLegToBasket({
                              index: activeIndex,
                              strike: row.strike,
                              optionType: 'PE',
                              expiry: selectedExpiry,
                              quote: row.pe,
                              lotSize: spec.lotSize,
                            })
                          }
                          data-tip={`Stage ${row.strike} PE (${selectedExpiry}) into Basket Builder with spread & liquidity metadata.`}
                          className="min-h-[32px] px-2 rounded-md border border-rose-500/40 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10 inline-flex items-center gap-1 font-sans text-[11px] font-semibold cursor-pointer"
                        >
                          <Plus className="w-3 h-3" /> PE
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Inside Tab 2: OI & Max Pain */}
      {insideTab === 'oi-profile' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                {spec.symbol} Call vs Put OI Wall ({selectedExpiry})
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Total Put OI {(totalPeOi / 100000).toFixed(1)}L vs Total Call OI {(totalCeOi / 100000).toFixed(1)}L · PCR {chainPcr}
              </p>
            </div>
          </div>
          <OptionOiProfileChart rows={rows} />
        </div>
      )}

      {/* Inside Tab 3: IV Skew & Greeks */}
      {insideTab === 'iv-greeks' && (
        <div className="space-y-5">
          <IvSurface3DChart spec={spec} />

          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800">
              <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
                Greeks, Spread Validation & Liquidity Quality ({selectedExpiry})
              </h2>
            </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs font-mono tabular-nums">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 text-left">
                  <th className="py-2.5 px-4 font-sans">Strike</th>
                  <th className="py-2.5 px-3 text-right">CE IV</th>
                  <th className="py-2.5 px-3 text-right">PE IV</th>
                  <th className="py-2.5 px-3 text-right">CE Theta</th>
                  <th className="py-2.5 px-3 text-right">CE Vega</th>
                  <th className="py-2.5 px-3 text-right">CE Spread</th>
                  <th className="py-2.5 px-4 text-right font-sans">Liquidity Guard</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {rows.map((r) => (
                  <tr key={r.strike} className={r.isAtm ? 'bg-sky-500/8' : ''}>
                    <td className="py-2.5 px-4 font-bold">{r.strike}</td>
                    <td className="py-2.5 px-3 text-right">{r.ce.iv}%</td>
                    <td className="py-2.5 px-3 text-right">{r.pe.iv}%</td>
                    <td className="py-2.5 px-3 text-right text-rose-600 dark:text-rose-400">
                      {r.ce.theta}
                    </td>
                    <td className="py-2.5 px-3 text-right">{r.ce.vega}</td>
                    <td className="py-2.5 px-3 text-right">{r.ce.spreadPct}%</td>
                    <td className="py-2.5 px-4 text-right font-sans">
                      {r.ce.spreadValid ? (
                        <span className="inline-flex items-center gap-1 text-emerald-600 dark:text-emerald-400 font-medium">
                          <ShieldCheck className="w-3.5 h-3.5" /> Pass ({r.ce.liquidityScore})
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                          <AlertCircle className="w-3.5 h-3.5" /> Wide ({r.ce.liquidityScore})
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          </div>
        </div>
      )}
    </div>
  );
};
