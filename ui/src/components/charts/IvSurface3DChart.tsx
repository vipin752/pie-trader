import React, { useMemo, useRef, useState } from 'react';
import * as d3 from 'd3';
import { RotateCcw } from 'lucide-react';
import { IndexMasterSpec, OptionChainRow } from '../../types/pie';
import { generateOptionChain } from '../../engine/pieEngine';

interface IvSurface3DChartProps {
  spec: IndexMasterSpec;
}

type SurfaceMode = 'mid' | 'ce' | 'pe';

interface SurfaceNode {
  strikeIdx: number;
  expiryIdx: number;
  strike: number;
  expiry: string;
  ceIv: number;
  peIv: number;
  midIv: number;
  ivValue: number;
  isAtm: boolean;
  xNorm: number;
  yNorm: number;
  zNorm: number;
}

interface ProjectedPoint {
  x2d: number;
  y2d: number;
  depth: number;
  node: SurfaceNode;
}

interface ProjectedFacet {
  key: string;
  pathD: string;
  avgDepth: number;
  avgIv: number;
  fill: string;
  centerNode: SurfaceNode;
}

export const IvSurface3DChart: React.FC<IvSurface3DChartProps> = ({ spec }) => {
  const [surfaceMode, setSurfaceMode] = useState<SurfaceMode>('mid');
  const [yawDeg, setYawDeg] = useState<number>(-38);
  const [pitchDeg, setPitchDeg] = useState<number>(26);
  const [hoveredNode, setHoveredNode] = useState<SurfaceNode | null>(null);
  const dragRef = useRef<{ active: boolean; x: number; y: number; yaw: number; pitch: number }>({
    active: false,
    x: 0,
    y: 0,
    yaw: -38,
    pitch: 26,
  });

  // Build strike x expiry matrix using authoritative option chain engine
  const matrixData = useMemo(() => {
    const expiryChains: { expiry: string; rows: OptionChainRow[] }[] = spec.expiries.map(
      (expiry, idx) => ({
        expiry,
        rows: generateOptionChain(spec, idx),
      })
    );

    const strikes = expiryChains[0]?.rows.map((r) => r.strike) ?? [];
    const allIvs: number[] = [];

    expiryChains.forEach(({ rows }) => {
      rows.forEach((r) => {
        const mid = Number(((r.ce.iv + r.pe.iv) / 2).toFixed(2));
        const val = surfaceMode === 'ce' ? r.ce.iv : surfaceMode === 'pe' ? r.pe.iv : mid;
        allIvs.push(val);
      });
    });

    const [minIv, maxIv] = d3.extent(allIvs) as [number, number];
    const ivSpan = Math.max(0.5, (maxIv ?? 20) - (minIv ?? 10));

    const zScale = d3
      .scaleLinear()
      .domain([minIv ?? 10, (minIv ?? 10) + ivSpan])
      .range([-0.55, 0.75]);

    const xScale = d3
      .scaleLinear()
      .domain([0, Math.max(1, strikes.length - 1)])
      .range([-1.15, 1.15]);

    const yScale = d3
      .scaleLinear()
      .domain([0, Math.max(1, expiryChains.length - 1)])
      .range([-0.85, 0.85]);

    const grid: SurfaceNode[][] = expiryChains.map(({ expiry, rows }, eIdx) =>
      rows.map((row, sIdx) => {
        const midIv = Number(((row.ce.iv + row.pe.iv) / 2).toFixed(2));
        const ivValue =
          surfaceMode === 'ce' ? row.ce.iv : surfaceMode === 'pe' ? row.pe.iv : midIv;
        return {
          strikeIdx: sIdx,
          expiryIdx: eIdx,
          strike: row.strike,
          expiry,
          ceIv: row.ce.iv,
          peIv: row.pe.iv,
          midIv,
          ivValue,
          isAtm: row.isAtm,
          xNorm: xScale(sIdx),
          yNorm: yScale(eIdx),
          zNorm: zScale(ivValue),
        };
      })
    );

    const colorScale = d3
      .scaleSequential(d3.interpolateRgbBasis(['#0EA5E9', '#14B8A6', '#F59E0B', '#EF4444']))
      .domain([minIv ?? 10, (minIv ?? 10) + ivSpan]);

    return {
      grid,
      strikes,
      expiries: spec.expiries,
      minIv: minIv ?? 10,
      maxIv: maxIv ?? 20,
      colorScale,
      zScale,
      xScale,
      yScale,
    };
  }, [spec, surfaceMode]);

  // Project 3D coordinates using D3 path & trigonometric camera matrix
  const projected = useMemo(() => {
    const width = 680;
    const height = 340;
    const cx = width / 2;
    const cy = height / 2 + 12;
    const scalePx = 152;

    const yaw = (yawDeg * Math.PI) / 180;
    const pitch = (pitchDeg * Math.PI) / 180;
    const cosY = Math.cos(yaw);
    const sinY = Math.sin(yaw);
    const cosP = Math.cos(pitch);
    const sinP = Math.sin(pitch);

    const projectPoint = (x: number, y: number, z: number) => {
      // Rotate around vertical Z-up -> screen Y-up
      const rx = x * cosY - y * sinY;
      const ry = x * sinY + y * cosY;
      const rz = z;

      const py = ry * cosP - rz * sinP;
      const pz = ry * sinP + rz * cosP;

      const perspective = 4.2 / (4.2 - py);
      return {
        x2d: cx + rx * scalePx * perspective,
        y2d: cy - pz * scalePx * perspective,
        depth: py,
      };
    };

    const projectedGrid: ProjectedPoint[][] = matrixData.grid.map((row) =>
      row.map((node) => {
        const p = projectPoint(node.xNorm, node.yNorm, node.zNorm);
        return { ...p, node };
      })
    );

    const pathGen = d3.line<[number, number]>().curve(d3.curveLinearClosed);
    const facets: ProjectedFacet[] = [];

    for (let e = 0; e < projectedGrid.length - 1; e++) {
      for (let s = 0; s < projectedGrid[e].length - 1; s++) {
        const p00 = projectedGrid[e][s];
        const p10 = projectedGrid[e][s + 1];
        const p11 = projectedGrid[e + 1][s + 1];
        const p01 = projectedGrid[e + 1][s];

        const avgDepth = (p00.depth + p10.depth + p11.depth + p01.depth) / 4;
        const avgIv =
          (p00.node.ivValue + p10.node.ivValue + p11.node.ivValue + p01.node.ivValue) / 4;

        const d =
          pathGen([
            [p00.x2d, p00.y2d],
            [p10.x2d, p10.y2d],
            [p11.x2d, p11.y2d],
            [p01.x2d, p01.y2d],
          ]) ?? '';

        facets.push({
          key: `${e}-${s}`,
          pathD: d,
          avgDepth,
          avgIv,
          fill: matrixData.colorScale(avgIv),
          centerNode: p00.node,
        });
      }
    }

    // Painter's algorithm depth sort (back to front)
    facets.sort((a, b) => a.avgDepth - b.avgDepth);

    // Project base floor axes & labels
    const zBase = -0.62;
    const strikeTicks = [0, 3, 6, 9, 12]
      .filter((idx) => idx < matrixData.strikes.length)
      .map((idx) => {
        const p = projectPoint(matrixData.xScale(idx), -0.95, zBase);
        return { strike: matrixData.strikes[idx], ...p };
      });

    const expiryTicks = matrixData.expiries.map((exp, idx) => {
      const p = projectPoint(1.26, matrixData.yScale(idx), zBase);
      return { expiry: exp, ...p };
    });

    const ivAxisTicks = [0, 0.5, 1].map((t) => {
      const ivVal = Number((matrixData.minIv + t * (matrixData.maxIv - matrixData.minIv)).toFixed(1));
      const p = projectPoint(-1.26, 0.92, matrixData.zScale(ivVal));
      return { ivVal, ...p };
    });

    // Base grid perimeter lines
    const baseLineGen = d3.line<[number, number]>();
    const floorOutline =
      baseLineGen([
        [projectPoint(-1.15, -0.85, zBase).x2d, projectPoint(-1.15, -0.85, zBase).y2d],
        [projectPoint(1.15, -0.85, zBase).x2d, projectPoint(1.15, -0.85, zBase).y2d],
        [projectPoint(1.15, 0.85, zBase).x2d, projectPoint(1.15, 0.85, zBase).y2d],
        [projectPoint(-1.15, 0.85, zBase).x2d, projectPoint(-1.15, 0.85, zBase).y2d],
        [projectPoint(-1.15, -0.85, zBase).x2d, projectPoint(-1.15, -0.85, zBase).y2d],
      ]) ?? '';

    const zAxisLine =
      baseLineGen([
        [projectPoint(-1.15, 0.85, zBase).x2d, projectPoint(-1.15, 0.85, zBase).y2d],
        [projectPoint(-1.15, 0.85, 0.8).x2d, projectPoint(-1.15, 0.85, 0.8).y2d],
      ]) ?? '';

    return {
      width,
      height,
      facets,
      projectedGrid,
      strikeTicks,
      expiryTicks,
      ivAxisTicks,
      floorOutline,
      zAxisLine,
    };
  }, [matrixData, yawDeg, pitchDeg]);

  const activeInspection =
    hoveredNode ??
    matrixData.grid[0]?.find((n) => n.isAtm) ??
    matrixData.grid[0]?.[0] ??
    null;

  const handlePointerDown = (e: React.PointerEvent<SVGSVGElement>) => {
    dragRef.current = {
      active: true,
      x: e.clientX,
      y: e.clientY,
      yaw: yawDeg,
      pitch: pitchDeg,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent<SVGSVGElement>) => {
    if (!dragRef.current.active) return;
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    setYawDeg(Math.max(-85, Math.min(85, dragRef.current.yaw + dx * 0.45)));
    setPitchDeg(Math.max(8, Math.min(72, dragRef.current.pitch + dy * 0.35)));
  };

  const handlePointerUp = (e: React.PointerEvent<SVGSVGElement>) => {
    dragRef.current.active = false;
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
  };

  return (
    <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <span>D3 3D Volatility Surface</span>
            <span aria-hidden="true">·</span>
            <span>{spec.symbol}</span>
            <span aria-hidden="true">·</span>
            <span>{matrixData.strikes.length} Strikes × {matrixData.expiries.length} Expiries</span>
            <span aria-hidden="true">·</span>
            <span>Drag surface to rotate</span>
          </div>
          <h2 className="font-display text-base font-bold text-slate-900 dark:text-white mt-0.5">
            Implied Volatility Surface (Strike × Expiry × IV%)
          </h2>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Option Side Selector */}
          <div className="inline-flex p-1 rounded-lg bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
            {(
              [
                { id: 'mid', label: 'Mid IV' },
                { id: 'ce', label: 'Call IV' },
                { id: 'pe', label: 'Put IV' },
              ] as { id: SurfaceMode; label: string }[]
            ).map((opt) => (
              <button
                key={opt.id}
                type="button"
                onClick={() => setSurfaceMode(opt.id)}
                className={`min-h-[32px] px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition-colors ${
                  surfaceMode === opt.id
                    ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                    : 'text-slate-600 dark:text-slate-400'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {/* Camera View Presets */}
          <div className="inline-flex p-1 rounded-lg bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
            <button
              type="button"
              onClick={() => {
                setYawDeg(-38);
                setPitchDeg(26);
              }}
              className="min-h-[32px] px-2.5 py-1 rounded-md text-xs text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 cursor-pointer inline-flex items-center gap-1"
            >
              <RotateCcw className="w-3 h-3" /> Iso
            </button>
            <button
              type="button"
              onClick={() => {
                setYawDeg(0);
                setPitchDeg(14);
              }}
              className="min-h-[32px] px-2.5 py-1 rounded-md text-xs text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 cursor-pointer"
            >
              Smile
            </button>
            <button
              type="button"
              onClick={() => {
                setYawDeg(-82);
                setPitchDeg(18);
              }}
              className="min-h-[32px] px-2.5 py-1 rounded-md text-xs text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-800 cursor-pointer"
            >
              Term
            </button>
          </div>
        </div>
      </div>

      {/* 3D Surface SVG Canvas */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-center">
        <div className="lg:col-span-9 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#080E1C] overflow-hidden">
          <svg
            viewBox={`0 0 ${projected.width} ${projected.height}`}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
            className="w-full h-64 sm:h-80 block touch-none select-none cursor-grab active:cursor-grabbing"
          >
            {/* Base Floor Frame & Z-Axis */}
            <path
              d={projected.floorOutline}
              fill="none"
              stroke="#64748B"
              strokeOpacity="0.3"
              strokeWidth="1"
            />
            <path
              d={projected.zAxisLine}
              fill="none"
              stroke="#64748B"
              strokeOpacity="0.45"
              strokeWidth="1"
            />

            {/* IV Axis Labels */}
            {projected.ivAxisTicks.map((t) => (
              <text
                key={t.ivVal}
                x={t.x2d - 6}
                y={t.y2d + 3}
                textAnchor="end"
                className="fill-slate-500 dark:fill-slate-400 text-[9px] font-mono"
              >
                {t.ivVal}%
              </text>
            ))}

            {/* Strike Axis Labels */}
            {projected.strikeTicks.map((t) => (
              <text
                key={t.strike}
                x={t.x2d}
                y={t.y2d + 12}
                textAnchor="middle"
                className="fill-slate-500 dark:fill-slate-400 text-[9px] font-mono"
              >
                {t.strike}
              </text>
            ))}

            {/* Expiry Axis Labels */}
            {projected.expiryTicks.map((t) => (
              <text
                key={t.expiry}
                x={t.x2d + 6}
                y={t.y2d + 4}
                textAnchor="start"
                className="fill-slate-500 dark:fill-slate-400 text-[9px] font-mono"
              >
                {t.expiry}
              </text>
            ))}

            {/* Depth-Sorted 3D Surface Quads */}
            {projected.facets.map((f) => {
              const isSelected =
                activeInspection &&
                activeInspection.strike === f.centerNode.strike &&
                activeInspection.expiry === f.centerNode.expiry;
              return (
                <path
                  key={f.key}
                  d={f.pathD}
                  fill={f.fill}
                  fillOpacity={0.84}
                  stroke={isSelected ? '#FFFFFF' : '#0F172A'}
                  strokeOpacity={isSelected ? 0.95 : 0.35}
                  strokeWidth={isSelected ? 1.6 : 0.75}
                  onMouseEnter={() => setHoveredNode(f.centerNode)}
                  onClick={() => setHoveredNode(f.centerNode)}
                  className="transition-opacity cursor-pointer"
                />
              );
            })}

            {/* Interactive Surface Nodes (ATM nodes highlighted) */}
            {projected.projectedGrid.flat().map((pt) => {
              const isSelected =
                activeInspection &&
                activeInspection.strike === pt.node.strike &&
                activeInspection.expiry === pt.node.expiry;
              return (
                <circle
                  key={`${pt.node.expiryIdx}-${pt.node.strikeIdx}`}
                  cx={pt.x2d}
                  cy={pt.y2d}
                  r={isSelected ? 4.2 : pt.node.isAtm ? 3 : 1.7}
                  fill={isSelected ? '#FFFFFF' : pt.node.isAtm ? '#38BDF8' : '#E2E8F0'}
                  fillOpacity={isSelected ? 1 : pt.node.isAtm ? 0.95 : 0.45}
                  stroke="#0F172A"
                  strokeWidth={isSelected ? 1.5 : 0.5}
                  onMouseEnter={() => setHoveredNode(pt.node)}
                  onClick={() => setHoveredNode(pt.node)}
                  className="cursor-pointer"
                />
              );
            })}
          </svg>
        </div>

        {/* Right Side: Live Surface Inspector & D3 Color Legend */}
        <div className="lg:col-span-3 space-y-4">
          {/* D3 Continuous IV Color Bar */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-[11px] font-mono tabular-nums text-slate-500 dark:text-slate-400">
              <span>IV Min {matrixData.minIv.toFixed(1)}%</span>
              <span>IV Max {matrixData.maxIv.toFixed(1)}%</span>
            </div>
            <div
              className="h-2.5 w-full rounded-full"
              style={{
                background: 'linear-gradient(90deg, #0EA5E9 0%, #14B8A6 35%, #F59E0B 70%, #EF4444 100%)',
              }}
            />
          </div>

          {activeInspection && (
            <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-[#0B1120] p-3.5 space-y-2.5 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-slate-500 dark:text-slate-400">Inspected Node</span>
                <span className="font-mono font-semibold text-sky-600 dark:text-sky-400">
                  {activeInspection.isAtm ? 'ATM Strike' : `${activeInspection.strike > spec.spot ? 'OTM Call' : 'OTM Put'}`}
                </span>
              </div>

              <div className="font-display font-bold text-sm text-slate-900 dark:text-white">
                {activeInspection.strike} · {activeInspection.expiry}
              </div>

              <div className="space-y-1.5 pt-2 border-t border-slate-200 dark:border-slate-800 font-mono tabular-nums">
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Mid IV</span>
                  <span className="font-bold text-slate-900 dark:text-white">
                    {activeInspection.midIv.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Call IV</span>
                  <span className="text-emerald-600 dark:text-emerald-400">
                    {activeInspection.ceIv.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Put IV</span>
                  <span className="text-rose-600 dark:text-rose-400">
                    {activeInspection.peIv.toFixed(2)}%
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-sans text-slate-500">Put-Call Skew</span>
                  <span>
                    {(activeInspection.peIv - activeInspection.ceIv >= 0 ? '+' : '') +
                      (activeInspection.peIv - activeInspection.ceIv).toFixed(2)}
                    %
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
