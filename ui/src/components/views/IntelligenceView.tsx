import React, { useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Sparkles,
  FileWarning,
  ShieldCheck,
  Loader2,
} from 'lucide-react';
import {
  CapitalDeploymentConfig,
  FundamentalMetric,
  IndexMasterSpec,
  IndexSymbol,
  IntelligenceInsideTab,
  NewsEvent,
  ProductFeedbackCategory,
  ProductFeedbackItem,
  ProductFeedbackStatus,
  PsychologyLog,
  StrategyAllocation,
  SuccessStory,
  TradeOpportunity,
  UserSession,
} from '../../types/pie';
import { AiTelemetrySnapshot, getStoredSessionToken } from '../../services/pieApiClient';
import { ExperienceAiHub } from './ExperienceAiHub';

interface IntelligenceViewProps {
  insideTab: IntelligenceInsideTab;
  activeIndex: IndexSymbol;
  indices: Record<IndexSymbol, IndexMasterSpec>;
  opportunities: TradeOpportunity[];
  newsEvents: NewsEvent[];
  fundamentals: FundamentalMetric[];
  session: UserSession | null;
  deploymentConfig: CapitalDeploymentConfig;
  strategies: StrategyAllocation[];
  psychology: PsychologyLog;
  stories: SuccessStory[];
  onAddSuccessStory: (story: Omit<SuccessStory, 'id' | 'createdAt' | 'verifiedByDesk'>) => void;
  feedback: ProductFeedbackItem[];
  onAddFeedback: (item: {
    title: string;
    category: ProductFeedbackCategory;
    description: string;
    source: ProductFeedbackItem['source'];
  }) => void;
  onUpvoteFeedback: (id: string) => void;
  onTransitionFeedback?: (id: string, nextStatus: ProductFeedbackStatus, testsVerified?: boolean) => void;
  telemetry?: AiTelemetrySnapshot | null;
  onRefreshTelemetry?: () => void;
}

const PIPELINE_STEPS = [
  '1. Global Intelligence',
  '2. Fundamental Context',
  '3. Market Regime',
  '4. Technical Structure',
  '5. Sentiment & Positioning',
  '6. Options Intelligence',
  '7. Probability Validation',
  '8. Risk Assessment',
  '9. Capital Allocation',
  '10. Trade Decision',
];

export const IntelligenceView: React.FC<IntelligenceViewProps> = ({
  insideTab,
  activeIndex,
  indices,
  opportunities,
  newsEvents,
  fundamentals,
  session,
  deploymentConfig,
  strategies,
  psychology,
  stories,
  onAddSuccessStory,
  feedback,
  onAddFeedback,
  onUpvoteFeedback,
  onTransitionFeedback,
  telemetry,
  onRefreshTelemetry,
}) => {
  const [hideStaleDuplicates, setHideStaleDuplicates] = useState(true);
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [aiBrief, setAiBrief] = useState<string>('');
  const [aiError, setAiError] = useState<string>('');
  const [aiLoading, setAiLoading] = useState(false);

  const spec = indices[activeIndex];
  const opp = opportunities.find((o) => o.index === activeIndex) ?? opportunities[0];

  const filteredNews = newsEvents.filter((n) => {
    if (hideStaleDuplicates && n.isStaleOrDuplicate) return false;
    if (verifiedOnly && !n.verified) return false;
    return true;
  });

  const handleGenerateBrief = async () => {
    setAiLoading(true);
    setAiError('');
    try {
      const token = getStoredSessionToken();
      const resp = await fetch('/api/research-brief', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'x-pie-session': token } : {}),
        },
        body: JSON.stringify({
          opportunity: opp,
          marketContext: spec,
          globalNews: filteredNews,
        }),
      });
      const data = await resp.json();
      if (!resp.ok) {
        setAiError(data.error || 'Unable to generate AI research synthesis.');
      } else {
        setAiBrief(data.brief || '');
      }
    } catch {
      setAiError('Network error reaching /api/research-brief.');
    } finally {
      setAiLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-bold text-slate-900 dark:text-white">
            {activeIndex} Intelligence & Unified PIE Decision Engine
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Context-aware 7-dimension evaluation. AI explains verified metrics and never fabricates prices or probabilities.
          </p>
        </div>
      </div>

      {/* Inside Tab 1: 7D Decision Pipeline */}
      {insideTab === 'pipeline' && (
        <div className="space-y-5">
          {/* 10-Stage Decision Pipeline Flow */}
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 sm:p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="text-xs text-slate-500 dark:text-slate-400">
                  Unified Decision Output for {opp.contract.tradingSymbol}
                </div>
                <h2 className="font-display text-lg font-bold text-slate-900 dark:text-white">
                  {opp.decision} · {opp.setupName}
                </h2>
              </div>

              <button
                type="button"
                onClick={handleGenerateBrief}
                disabled={aiLoading}
                data-tip-title="Gemini AI Research Brief"
                data-tip="Calls server-side Gemini 4 Hydrogen to summarize verified alignment, invalidation, and scaling rules without fabricating numbers."
                className="min-h-[40px] px-3.5 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center gap-2 disabled:opacity-60 cursor-pointer"
              >
                {aiLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
                <span>Synthesize AI Research Brief</span>
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              {PIPELINE_STEPS.map((step, i) => {
                const isVetoed = opp.decision === 'RISK BLOCKED' && i >= 7;
                const isInsufficient = opp.decision === 'NO TRADE' && i >= 6;
                return (
                  <div
                    key={step}
                    className={`p-2.5 rounded-lg border text-xs ${
                      isVetoed
                        ? 'border-rose-500/40 bg-rose-500/8 text-rose-700 dark:text-rose-300'
                        : isInsufficient
                        ? 'border-amber-500/40 bg-amber-500/8 text-amber-700 dark:text-amber-300'
                        : 'border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-[#0B1120] text-slate-700 dark:text-slate-300'
                    }`}
                  >
                    <div className="font-medium truncate">{step}</div>
                    <div className="text-[11px] opacity-80 mt-0.5">
                      {isVetoed ? 'Veto Triggered' : isInsufficient ? 'No Edge / Low n' : 'Verified'}
                    </div>
                  </div>
                );
              })}
            </div>

            {aiError && (
              <div className="p-3 rounded-lg border border-amber-500/40 bg-amber-500/8 text-xs text-amber-700 dark:text-amber-300">
                {aiError}
              </div>
            )}
            {aiBrief && (
              <div className="p-4 rounded-lg border border-sky-500/30 bg-sky-500/5 text-xs sm:text-sm text-slate-700 dark:text-slate-200 whitespace-pre-line leading-relaxed">
                {aiBrief}
              </div>
            )}
          </div>

          {/* 7-Dimension Weighted Evaluation */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
            <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-3">
              <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
                7-Dimension Evidence Calibration
              </h3>
              <div className="space-y-3">
                {[
                  { label: 'Fundamental Context', score: opp.dimensionScores.fundamental },
                  { label: 'Technical & SMC Structure', score: opp.dimensionScores.technical },
                  { label: 'Global-to-India News', score: opp.dimensionScores.globalNews },
                  { label: 'Sentiment & Positioning', score: opp.dimensionScores.sentiment },
                  { label: 'Statistical Probability', score: opp.dimensionScores.probability },
                  { label: 'Risk & Liquidity Fit', score: opp.dimensionScores.riskFit },
                  { label: 'Discipline & Session Fit', score: opp.dimensionScores.disciplineFit },
                ].map((dim) => (
                  <div key={dim.label} className="space-y-1">
                    <div className="flex justify-between text-xs">
                      <span className="text-slate-600 dark:text-slate-300">{dim.label}</span>
                      <span className="font-mono tabular-nums font-semibold">{dim.score}/100</span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
                      <div
                        className={`h-full rounded-full ${
                          dim.score >= 75
                            ? 'bg-emerald-500'
                            : dim.score >= 55
                            ? 'bg-sky-500'
                            : 'bg-rose-500'
                        }`}
                        style={{ width: `${dim.score}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-5 space-y-4 text-xs">
              <h3 className="font-display text-sm font-bold text-slate-900 dark:text-white">
                Explainability Audit: Positive, Negative & Missing Evidence
              </h3>
              <div className="space-y-3">
                <div>
                  <div className="font-semibold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 mb-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Supporting Evidence
                  </div>
                  <ul className="space-y-1 text-slate-600 dark:text-slate-300">
                    {opp.positiveEvidence.map((e) => (
                      <li key={e}>• {e}</li>
                    ))}
                  </ul>
                </div>
                <div className="pt-2 border-t border-slate-200 dark:border-slate-800">
                  <div className="font-semibold text-rose-600 dark:text-rose-400 flex items-center gap-1.5 mb-1">
                    <AlertTriangle className="w-3.5 h-3.5" /> Adverse / Contradictory Factors
                  </div>
                  <ul className="space-y-1 text-slate-600 dark:text-slate-300">
                    {opp.negativeEvidence.map((e) => (
                      <li key={e}>• {e}</li>
                    ))}
                  </ul>
                </div>
                <div className="pt-2 border-t border-slate-200 dark:border-slate-800">
                  <div className="font-semibold text-amber-600 dark:text-amber-400 flex items-center gap-1.5 mb-1">
                    <FileWarning className="w-3.5 h-3.5" /> Missing Evidence & Hard Invalidation
                  </div>
                  <ul className="space-y-1 text-slate-600 dark:text-slate-300">
                    {opp.missingEvidence.map((e) => (
                      <li key={e}>• {e}</li>
                    ))}
                    <li className="font-semibold text-slate-900 dark:text-white">
                      Invalidation: {opp.tradePlan.invalidationCondition}
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Inside Tab 2: Global-to-India News */}
      {insideTab === 'news' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="text-xs text-slate-500 dark:text-slate-400">
              Headlines require price confirmation before triggering entries. Stale & duplicate stories are isolated.
            </div>
            <div className="flex items-center gap-3 text-xs">
              <label className="inline-flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={hideStaleDuplicates}
                  onChange={(e) => setHideStaleDuplicates(e.target.checked)}
                  className="rounded border-slate-400"
                />
                <span>Hide Stale/Duplicate</span>
              </label>
              <label className="inline-flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={verifiedOnly}
                  onChange={(e) => setVerifiedOnly(e.target.checked)}
                  className="rounded border-slate-400"
                />
                <span>Verified Only</span>
              </label>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] divide-y divide-slate-200 dark:divide-slate-800">
            {filteredNews.map((item) => (
              <div key={item.id} className="p-4 sm:p-5 space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={`font-semibold ${
                        item.impact === 'High'
                          ? 'text-rose-600 dark:text-rose-400'
                          : item.impact === 'Medium'
                          ? 'text-amber-600 dark:text-amber-400'
                          : 'text-slate-500'
                      }`}
                    >
                      {item.impact} Impact
                    </span>
                    <span aria-hidden="true">·</span>
                    <span>{item.source}</span>
                    <span aria-hidden="true">·</span>
                    <span className="font-mono">{item.publishedAt}</span>
                    <span aria-hidden="true">·</span>
                    <span>{item.verified ? 'Verified' : 'Unverified'}</span>
                    {item.alreadyPricedIn && (
                      <>
                        <span aria-hidden="true">·</span>
                        <span>Priced In</span>
                      </>
                    )}
                  </div>
                  <span className="font-mono tabular-nums">
                    Relevance {item.relevanceConfidence}%
                  </span>
                </div>

                <h3 className="font-display text-sm sm:text-base font-semibold text-slate-900 dark:text-white">
                  {item.headline}
                </h3>

                <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                  <span className="text-slate-500 dark:text-slate-400">
                    Affected: {item.affectedMarket} · {item.confirmationNote}
                  </span>
                  <span
                    className={`font-semibold ${
                      item.directionalImplication === 'Bullish India'
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : item.directionalImplication === 'Bearish India'
                        ? 'text-rose-600 dark:text-rose-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {item.directionalImplication}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Inside Tab 3: Fundamental Macro */}
      {insideTab === 'macro' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 text-xs text-slate-600 dark:text-slate-300 flex items-center gap-2.5">
            <ShieldCheck className="w-4 h-4 text-sky-500 shrink-0" />
            <span>
              Guardrail active: Slow-moving macro factors shape directional bias and regime sizing—they never independently trigger an intraday option entry without price-action confirmation.
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {fundamentals.map((f) => (
              <div
                key={f.id}
                className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-4 space-y-2"
              >
                <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                  <span>{f.horizon}</span>
                  <span
                    className={`font-semibold ${
                      f.bias === 'Supportive'
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : f.bias === 'Headwind'
                        ? 'text-rose-600 dark:text-rose-400'
                        : 'text-amber-600 dark:text-amber-400'
                    }`}
                  >
                    {f.bias}
                  </span>
                </div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white">{f.label}</div>
                <div className="font-mono text-lg font-bold tabular-nums text-slate-900 dark:text-white">
                  {f.value}{' '}
                  <span className="text-xs font-normal text-slate-500 dark:text-slate-400">
                    ({f.delta})
                  </span>
                </div>
                <p className="text-xs text-slate-600 dark:text-slate-400">{f.note}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Inside Tab 4: PIE Probability Engine */}
      {insideTab === 'probability' && (
        <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800">
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white">
              Out-of-Sample Calibrated Probability & Expectancy
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              AI confidence scores are never equated with statistically validated probabilities. Setups with n &lt; 30 observations display INSUFFICIENT HISTORICAL EVIDENCE.
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs font-mono tabular-nums">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 text-left">
                  <th className="py-3 px-4 font-sans">Contract & Setup</th>
                  <th className="py-3 px-3 text-right">P(T1)</th>
                  <th className="py-3 px-3 text-right">P(T2)</th>
                  <th className="py-3 px-3 text-right">P(SL First)</th>
                  <th className="py-3 px-3 text-right">Net EV / Trade</th>
                  <th className="py-3 px-3 text-right">Sample n</th>
                  <th className="py-3 px-4 text-right font-sans">Calibration</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                {opportunities.map((o) => (
                  <tr key={o.id}>
                    <td className="py-3 px-4 font-sans">
                      <div className="font-semibold text-slate-900 dark:text-white">
                        {o.contract.tradingSymbol}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        {o.probability.lookbackPeriod} · ±{o.probability.uncertaintyBandPct}%
                      </div>
                    </td>
                    <td className="py-3 px-3 text-right text-emerald-600 dark:text-emerald-400">
                      {o.probability.probTarget1}%
                    </td>
                    <td className="py-3 px-3 text-right">{o.probability.probTarget2}%</td>
                    <td className="py-3 px-3 text-right text-rose-600 dark:text-rose-400">
                      {o.probability.probStopFirst}%
                    </td>
                    <td className="py-3 px-3 text-right font-semibold">
                      ₹{o.probability.expectedValueInr.toLocaleString('en-IN')}
                    </td>
                    <td className="py-3 px-3 text-right">{o.probability.sampleSize}</td>
                    <td className="py-3 px-4 text-right font-sans">
                      {o.probability.sufficientEvidence ? (
                        <span className="text-emerald-600 dark:text-emerald-400 font-semibold">
                          Validated ({o.probability.regimeConditioned})
                        </span>
                      ) : (
                        <span className="text-amber-600 dark:text-amber-400 font-semibold">
                          INSUFFICIENT HISTORICAL EVIDENCE
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Inside Tab 5: Gen AI Experience & Global/Internal Success Hub */}
      {insideTab === 'experience-ai' && (
        <ExperienceAiHub
          session={session}
          activeIndex={activeIndex}
          deploymentConfig={deploymentConfig}
          strategies={strategies}
          psychology={psychology}
          stories={stories}
          onAddSuccessStory={onAddSuccessStory}
          feedback={feedback}
          onAddFeedback={onAddFeedback}
          onUpvoteFeedback={onUpvoteFeedback}
          onTransitionFeedback={onTransitionFeedback}
          telemetry={telemetry}
          onRefreshTelemetry={onRefreshTelemetry}
        />
      )}
    </div>
  );
};
