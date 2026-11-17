import React, { useMemo, useState } from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
} from 'recharts';
import { DailyPnlSessionPoint } from '../../types/pie';

interface CumulativePnlChartProps {
  sessions: DailyPnlSessionPoint[];
  liveSessionNetPnlInr: number;
}

type ChartMetricView = 'cumulative' | 'daily';

interface TooltipPayloadItem {
  payload: DailyPnlSessionPoint;
}

const CustomPnlTooltip: React.FC<{
  active?: boolean;
  payload?: TooltipPayloadItem[];
}> = ({ active, payload }) => {
  if (!active || !payload || !payload.length) return null;
  const pt = payload[0].payload;

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#0B1120] px-3.5 py-2.5 shadow-md text-xs space-y-1">
      <div className="font-display font-semibold text-slate-900 dark:text-white flex items-center justify-between gap-4">
        <span>Session {pt.sessionIndex} · {pt.dateLabel}</span>
        <span className="text-[11px] font-normal text-slate-500 dark:text-slate-400">
          {pt.tradesCount} trades
        </span>
      </div>
      <div className="font-mono tabular-nums flex items-center justify-between gap-5">
        <span className="text-slate-500 dark:text-slate-400 font-sans">Cumulative P&L</span>
        <span
          className={`font-semibold ${
            pt.cumulativePnlInr >= 0
              ? 'text-sky-600 dark:text-sky-400'
              : 'text-rose-600 dark:text-rose-400'
          }`}
        >
          {pt.cumulativePnlInr >= 0 ? '+' : ''}₹{pt.cumulativePnlInr.toLocaleString('en-IN')}
        </span>
      </div>
      <div className="font-mono tabular-nums flex items-center justify-between gap-5">
        <span className="text-slate-500 dark:text-slate-400 font-sans">Session Net P&L</span>
        <span
          className={
            pt.dailyNetPnlInr >= 0
              ? 'text-sky-600 dark:text-sky-400'
              : 'text-rose-600 dark:text-rose-400'
          }
        >
          {pt.dailyNetPnlInr >= 0 ? '+' : ''}₹{pt.dailyNetPnlInr.toLocaleString('en-IN')}
        </span>
      </div>
      <div className="font-mono tabular-nums flex items-center justify-between gap-5">
        <span className="text-slate-500 dark:text-slate-400 font-sans">Drawdown</span>
        <span className="text-slate-700 dark:text-slate-300">
          ₹{pt.drawdownFromPeakInr.toLocaleString('en-IN')}
        </span>
      </div>
    </div>
  );
};

export const CumulativePnlChart: React.FC<CumulativePnlChartProps> = ({
  sessions,
  liveSessionNetPnlInr,
}) => {
  const [viewMode, setViewMode] = useState<ChartMetricView>('cumulative');

  const chartData = useMemo(() => {
    if (!sessions.length) return [];
    const basePrior = sessions.slice(0, -1);
    const priorCumulative = basePrior.length ? basePrior[basePrior.length - 1].cumulativePnlInr : 0;
    let peak = basePrior.reduce((m, s) => Math.max(m, s.cumulativePnlInr), 0);

    const lastTemplate = sessions[sessions.length - 1];
    const updatedLastCum = Number((priorCumulative + liveSessionNetPnlInr).toFixed(2));
    peak = Math.max(peak, updatedLastCum);

    return [
      ...basePrior,
      {
        ...lastTemplate,
        dailyNetPnlInr: Number(liveSessionNetPnlInr.toFixed(2)),
        cumulativePnlInr: updatedLastCum,
        drawdownFromPeakInr: Number((peak - updatedLastCum).toFixed(2)),
      },
    ];
  }, [sessions, liveSessionNetPnlInr]);

  const stats = useMemo(() => {
    if (!chartData.length) {
      return { finalCum: 0, peakCum: 0, maxDd: 0, winSessions: 0 };
    }
    const finalCum = chartData[chartData.length - 1].cumulativePnlInr;
    const peakCum = chartData.reduce((m, d) => Math.max(m, d.cumulativePnlInr), 0);
    const maxDd = chartData.reduce((m, d) => Math.max(m, d.drawdownFromPeakInr), 0);
    const winSessions = chartData.filter((d) => d.dailyNetPnlInr > 0).length;
    return { finalCum, peakCum, maxDd, winSessions };
  }, [chartData]);

  const dataKey = viewMode === 'cumulative' ? 'cumulativePnlInr' : 'dailyNetPnlInr';
  const strokeColor = viewMode === 'cumulative' ? '#0EA5E9' : '#0284C7';

  return (
    <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <span>Last 30 Trading Sessions</span>
            <span aria-hidden="true">·</span>
            <span>Net of Brokerage & Statutory Taxes</span>
            <span aria-hidden="true">·</span>
            <span className="font-mono tabular-nums">
              {stats.winSessions}/{chartData.length} Positive Sessions
            </span>
          </div>
          <h2 className="font-display text-base font-bold text-slate-900 dark:text-white mt-0.5">
            Cumulative Daily P&L Trajectory
          </h2>
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <div className="flex items-center gap-4 text-xs font-mono tabular-nums">
            <div>
              <span className="font-sans text-slate-500 dark:text-slate-400 mr-1.5">30D Net:</span>
              <span
                className={`font-semibold ${
                  stats.finalCum >= 0
                    ? 'text-sky-600 dark:text-sky-400'
                    : 'text-rose-600 dark:text-rose-400'
                }`}
              >
                {stats.finalCum >= 0 ? '+' : ''}₹{stats.finalCum.toLocaleString('en-IN')}
              </span>
            </div>
            <div className="hidden sm:block">
              <span className="font-sans text-slate-500 dark:text-slate-400 mr-1.5">Max DD:</span>
              <span className="font-semibold text-rose-600 dark:text-rose-400">
                ₹{stats.maxDd.toLocaleString('en-IN')}
              </span>
            </div>
          </div>

          <div className="inline-flex p-1 rounded-lg bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
            <button
              type="button"
              onClick={() => setViewMode('cumulative')}
              className={`min-h-[32px] px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition-colors ${
                viewMode === 'cumulative'
                  ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              Cumulative
            </button>
            <button
              type="button"
              onClick={() => setViewMode('daily')}
              className={`min-h-[32px] px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition-colors ${
                viewMode === 'daily'
                  ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                  : 'text-slate-600 dark:text-slate-400'
              }`}
            >
              Daily Net
            </button>
          </div>
        </div>
      </div>

      <div className="w-full h-64 sm:h-72 pt-1">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 10, right: 12, left: 4, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#64748B" strokeOpacity={0.18} vertical={false} />
            <XAxis
              dataKey="dateLabel"
              tick={{ fill: '#64748B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}
              tickLine={false}
              axisLine={{ stroke: '#64748B', strokeOpacity: 0.25 }}
              interval={4}
            />
            <YAxis
              tickFormatter={(val: number) => `₹${(val / 1000).toFixed(0)}k`}
              tick={{ fill: '#64748B', fontSize: 11, fontFamily: 'JetBrains Mono, monospace' }}
              tickLine={false}
              axisLine={false}
              width={52}
            />
            <Tooltip content={<CustomPnlTooltip />} />
            <ReferenceLine y={0} stroke="#64748B" strokeOpacity={0.4} strokeDasharray="4 4" />
            <Line
              type="monotone"
              dataKey={dataKey}
              stroke={strokeColor}
              strokeWidth={2.4}
              dot={{ r: 2.2, strokeWidth: 1.5, fill: strokeColor }}
              activeDot={{ r: 5, strokeWidth: 2, fill: '#FFFFFF', stroke: strokeColor }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
