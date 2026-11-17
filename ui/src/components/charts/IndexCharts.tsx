import React, { useState } from 'react';
import { CandlePoint, OptionChainRow } from '../../types/pie';

interface PriceStructureChartProps {
  candles: CandlePoint[];
  pdh: number;
  pdl: number;
  fvgZone: [number, number];
  symbol: string;
}

export const PriceStructureChart: React.FC<PriceStructureChartProps> = ({
  candles,
  pdh,
  pdl,
  fvgZone,
  symbol,
}) => {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  if (!candles.length) return null;

  const width = 680;
  const height = 240;
  const padLeft = 14;
  const padRight = 56;
  const padTop = 18;
  const padBottom = 26;

  const allValues = candles.flatMap((c) => [c.high, c.low, c.vwap]).concat([pdh, pdl, fvgZone[0], fvgZone[1]]);
  const minPrice = Math.min(...allValues);
  const maxPrice = Math.max(...allValues);
  const range = Math.max(1, maxPrice - minPrice);

  const innerW = width - padLeft - padRight;
  const innerH = height - padTop - padBottom;

  const yOf = (price: number) => padTop + ((maxPrice - price) / range) * innerH;
  const xStep = innerW / candles.length;

  const vwapPath = candles
    .map((c, idx) => `${idx === 0 ? 'M' : 'L'} ${(padLeft + idx * xStep + xStep / 2).toFixed(1)} ${yOf(c.vwap).toFixed(1)}`)
    .join(' ');

  const fvgTop = yOf(Math.max(fvgZone[0], fvgZone[1]));
  const fvgBottom = yOf(Math.min(fvgZone[0], fvgZone[1]));
  const activeCandle = hoverIdx !== null ? candles[hoverIdx] : candles[candles.length - 1];

  return (
    <div className="w-full">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2 text-xs">
        <div className="flex items-center gap-3 text-slate-600 dark:text-slate-300 font-mono tabular-nums">
          <span className="font-semibold text-slate-900 dark:text-white">{symbol} · 15m</span>
          <span>O {activeCandle.open.toLocaleString('en-IN')}</span>
          <span>H {activeCandle.high.toLocaleString('en-IN')}</span>
          <span>L {activeCandle.low.toLocaleString('en-IN')}</span>
          <span className="font-semibold text-sky-600 dark:text-sky-400">
            C {activeCandle.close.toLocaleString('en-IN')}
          </span>
          <span className="text-amber-600 dark:text-amber-400">
            VWAP {activeCandle.vwap.toLocaleString('en-IN')}
          </span>
        </div>
        <div className="flex items-center gap-4 text-[11px] text-slate-500 dark:text-slate-400">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-0.5 bg-amber-500" /> VWAP
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-0.5 border-t border-dashed border-sky-400" /> PDH/PDL
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-2 bg-emerald-500/20 border border-emerald-500/40" /> FVG Zone
          </span>
        </div>
      </div>

      <div className="relative w-full overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#080E1C]">
        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-56 sm:h-60 block select-none">
          {/* Grid lines & right-axis labels */}
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const p = maxPrice - ratio * range;
            const y = padTop + ratio * innerH;
            return (
              <g key={ratio}>
                <line
                  x1={padLeft}
                  y1={y}
                  x2={width - padRight}
                  y2={y}
                  stroke="currentColor"
                  className="text-slate-200 dark:text-slate-800/80"
                  strokeWidth="1"
                />
                <text
                  x={width - padRight + 6}
                  y={y + 3}
                  className="fill-slate-400 dark:fill-slate-500 text-[9px] font-mono"
                >
                  {p.toFixed(0)}
                </text>
              </g>
            );
          })}

          {/* FVG shaded zone */}
          <rect
            x={padLeft}
            y={fvgTop}
            width={innerW}
            height={Math.max(4, fvgBottom - fvgTop)}
            className="fill-emerald-500/12"
          />

          {/* PDH & PDL lines */}
          <line
            x1={padLeft}
            y1={yOf(pdh)}
            x2={width - padRight}
            y2={yOf(pdh)}
            stroke="#38BDF8"
            strokeDasharray="4 4"
            strokeWidth="1"
          />
          <text x={padLeft + 4} y={yOf(pdh) - 4} className="fill-sky-500 dark:fill-sky-400 text-[9px] font-mono">
            PDH {pdh}
          </text>

          <line
            x1={padLeft}
            y1={yOf(pdl)}
            x2={width - padRight}
            y2={yOf(pdl)}
            stroke="#94A3B8"
            strokeDasharray="3 3"
            strokeWidth="1"
          />

          {/* VWAP line */}
          <path d={vwapPath} fill="none" stroke="#F59E0B" strokeWidth="1.6" />

          {/* Candlesticks */}
          {candles.map((c, idx) => {
            const cx = padLeft + idx * xStep + xStep / 2;
            const isUp = c.close >= c.open;
            const bodyTop = yOf(Math.max(c.open, c.close));
            const bodyBottom = yOf(Math.min(c.open, c.close));
            const bodyH = Math.max(2, bodyBottom - bodyTop);
            const barW = Math.max(5, Math.min(14, xStep * 0.56));
            const color = isUp ? '#10B981' : '#EF4444';

            return (
              <g
                key={c.time}
                onMouseEnter={() => setHoverIdx(idx)}
                onMouseLeave={() => setHoverIdx(null)}
                onClick={() => setHoverIdx(idx)}
                className="cursor-crosshair"
              >
                <rect
                  x={padLeft + idx * xStep}
                  y={padTop}
                  width={xStep}
                  height={innerH}
                  fill="transparent"
                />
                {hoverIdx === idx && (
                  <line
                    x1={cx}
                    y1={padTop}
                    x2={cx}
                    y2={height - padBottom}
                    stroke="#64748B"
                    strokeDasharray="2 2"
                    strokeWidth="1"
                  />
                )}
                <line x1={cx} y1={yOf(c.high)} x2={cx} y2={yOf(c.low)} stroke={color} strokeWidth="1.3" />
                <rect x={cx - barW / 2} y={bodyTop} width={barW} height={bodyH} rx="1.5" fill={color} />
                {idx % 3 === 0 && (
                  <text
                    x={cx}
                    y={height - 8}
                    textAnchor="middle"
                    className="fill-slate-400 dark:fill-slate-500 text-[9px] font-mono"
                  >
                    {c.time}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
};

interface OptionOiProfileChartProps {
  rows: OptionChainRow[];
}

export const OptionOiProfileChart: React.FC<OptionOiProfileChartProps> = ({ rows }) => {
  const maxOi = Math.max(...rows.flatMap((r) => [r.ce.oi, r.pe.oi]), 1);

  return (
    <div className="space-y-2.5">
      <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
        <span>Open Interest Distribution by Strike</span>
        <div className="flex items-center gap-4">
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-xs bg-rose-500/80 inline-block" /> Call OI (Resistance)
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-xs bg-emerald-500/80 inline-block" /> Put OI (Support)
          </span>
        </div>
      </div>

      <div className="space-y-1.5">
        {rows.map((row) => {
          const cePct = Math.round((row.ce.oi / maxOi) * 100);
          const pePct = Math.round((row.pe.oi / maxOi) * 100);
          return (
            <div
              key={row.strike}
              className={`grid grid-cols-[1fr_76px_1fr] items-center gap-2 py-1 px-2 rounded-md text-xs font-mono tabular-nums ${
                row.isAtm ? 'bg-sky-500/10 ring-1 ring-sky-500/30' : ''
              }`}
            >
              <div className="flex items-center justify-end gap-2">
                <span className="text-[11px] text-slate-500 dark:text-slate-400 hidden sm:inline">
                  {(row.ce.oi / 100000).toFixed(1)}L
                </span>
                <div className="w-full max-w-[220px] h-2.5 bg-slate-200 dark:bg-slate-800 rounded-xs overflow-hidden flex justify-end">
                  <div className="h-full bg-rose-500/85" style={{ width: `${cePct}%` }} />
                </div>
              </div>

              <div className="text-center font-semibold text-slate-900 dark:text-slate-100">
                {row.strike}
                {row.isAtm ? ' *' : ''}
              </div>

              <div className="flex items-center gap-2">
                <div className="w-full max-w-[220px] h-2.5 bg-slate-200 dark:bg-slate-800 rounded-xs overflow-hidden">
                  <div className="h-full bg-emerald-500/85" style={{ width: `${pePct}%` }} />
                </div>
                <span className="text-[11px] text-slate-500 dark:text-slate-400 hidden sm:inline">
                  {(row.pe.oi / 100000).toFixed(1)}L
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

interface EquityCurveChartProps {
  curves: { label: string; color: string; data: number[] }[];
}

export const EquityCurveChart: React.FC<EquityCurveChartProps> = ({ curves }) => {
  const all = curves.flatMap((c) => c.data);
  if (!all.length) return null;
  const minVal = Math.min(...all) * 0.995;
  const maxVal = Math.max(...all) * 1.005;
  const range = Math.max(1, maxVal - minVal);
  const w = 600;
  const h = 180;
  const pad = 20;

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#080E1C] p-3">
      <div className="flex flex-wrap items-center gap-4 mb-2 text-xs">
        {curves.map((c) => (
          <span key={c.label} className="flex items-center gap-1.5 text-slate-600 dark:text-slate-300">
            <span className="w-3 h-0.5 inline-block" style={{ backgroundColor: c.color }} />
            {c.label}
          </span>
        ))}
      </div>
      <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-44 block">
        {[0, 0.5, 1].map((t) => {
          const y = pad + t * (h - pad * 2);
          const val = maxVal - t * range;
          return (
            <g key={t}>
              <line x1={pad} y1={y} x2={w - 58} y2={y} stroke="currentColor" className="text-slate-200 dark:text-slate-800" />
              <text x={w - 54} y={y + 3} className="fill-slate-400 text-[9px] font-mono">
                ₹{(val / 100000).toFixed(2)}L
              </text>
            </g>
          );
        })}
        {curves.map((curve) => {
          const step = (w - pad - 64) / Math.max(1, curve.data.length - 1);
          const d = curve.data
            .map((val, i) => {
              const x = pad + i * step;
              const y = pad + ((maxVal - val) / range) * (h - pad * 2);
              return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
            })
            .join(' ');
          return <path key={curve.label} d={d} fill="none" stroke={curve.color} strokeWidth="2.2" />;
        })}
      </svg>
    </div>
  );
};
