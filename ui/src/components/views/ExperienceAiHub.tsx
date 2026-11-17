import React, { useState } from 'react';
import {
  Sparkles,
  Send,
  Loader2,
  Award,
  Globe2,
  Lightbulb,
  ThumbsUp,
  Plus,
  CheckCircle2,
  MessageSquarePlus,
  Cpu,
  ShieldCheck,
} from 'lucide-react';
import {
  CapitalDeploymentConfig,
  ExperienceChatMessage,
  GeminiChatModel,
  IndexSymbol,
  ProductFeedbackCategory,
  ProductFeedbackItem,
  ProductFeedbackStatus,
  PsychologyLog,
  StrategyAllocation,
  SuccessStory,
  UserSession,
} from '../../types/pie';
import { AiTelemetrySnapshot, getStoredSessionToken } from '../../services/pieApiClient';

interface ExperienceAiHubProps {
  session: UserSession | null;
  activeIndex: IndexSymbol;
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

const STARTER_PROMPTS = [
  'I exited NIFTY CE early before Target 1 out of fear. How do successful PIE & global traders handle this?',
  'I want to scale into BANKNIFTY momentum without exceeding my ₹2,00,000 authorized allocation.',
  'On mobile it takes too many taps to trail stop-loss to breakeven after Target 1 — can we improve the product?',
];

const FEEDBACK_CATEGORIES: ProductFeedbackCategory[] = [
  'Execution & Risk',
  'Capital & Sizing',
  'Intelligence & AI',
  'UX & Mobile',
];

export const ExperienceAiHub: React.FC<ExperienceAiHubProps> = ({
  session,
  activeIndex,
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
  const [selectedModel, setSelectedModel] = useState<GeminiChatModel>('gemini-4-hydrogen');
  const [messages, setMessages] = useState<ExperienceChatMessage[]>([
    {
      id: 'msg-welcome',
      role: 'model',
      timestamp: 'Desk Ready',
      modelUsed: 'gemini-4-hydrogen',
      domain: 'TRADING_INTELLIGENCE',
      text:
        'Share your trading experience, wins, drawdowns, or workflow friction. TradingIntelligenceAgent reads your authoritative capital summary & PositionSizingEngine outputs first, compares against anonymized Internal PIE Desk and Global Benchmark cohorts, and routes any product ideas separately to ProductDevelopmentAgent.',
      referencedStories: [
        { id: stories[0]?.id ?? 'story-int-1', traderAlias: stories[0]?.traderAlias ?? 'Aarav M.', scope: 'Internal PIE Desk' },
        { id: stories[3]?.id ?? 'story-glb-1', traderAlias: stories[3]?.traderAlias ?? 'Chicago SPX Prop Cohort', scope: 'Global Benchmark' },
      ],
      suggestedActions: [
        'Share a recent trade outcome or emotional challenge to compare against peer adherence.',
        'Ask which global or internal sizing rule best fits your current strategy sleeve.',
      ],
    },
  ]);
  const [inputText, setInputText] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [chatError, setChatError] = useState('');

  const [rightTab, setRightTab] = useState<'stories' | 'roadmap' | 'telemetry'>('stories');
  const [storyFilter, setStoryFilter] = useState<'All' | 'Internal PIE Desk' | 'Global Benchmark'>('All');
  const [highlightedStoryId, setHighlightedStoryId] = useState<string | null>(null);

  const [showStoryForm, setShowStoryForm] = useState(false);
  const [storyForm, setStoryForm] = useState({
    traderAlias: session?.displayName ? `${session.displayName} (Desk)` : 'Desk Trader',
    marketFocus: activeIndex,
    strategyUsed: strategies[0]?.name ?? 'NIFTY Intraday SMC',
    capitalDeployedLabel: `₹${deploymentConfig.authorizedAllocationInr.toLocaleString('en-IN')} authorized`,
    riskPerTradePct: 1.0,
    winRatePct: 60,
    maxDrawdownPct: 3.5,
    netReturnLabel: '+11.4% / 30 sessions',
    adherenceScore: psychology.ruleAdherencePct,
    keyLesson: '',
    fullExperience: '',
  });

  const [showFeedbackForm, setShowFeedbackForm] = useState(false);
  const [feedbackForm, setFeedbackForm] = useState<{
    title: string;
    category: ProductFeedbackCategory;
    description: string;
  }>({
    title: '',
    category: 'Execution & Risk',
    description: '',
  });

  const [insightsText, setInsightsText] = useState('');
  const [insightsLoading, setInsightsLoading] = useState(false);
  const [insightsError, setInsightsError] = useState('');

  const sendMessage = async (textOverride?: string) => {
    const text = (textOverride ?? inputText).trim();
    if (!text || chatLoading) return;

    const userMsg: ExperienceChatMessage = {
      id: `u-${Date.now()}`,
      role: 'user',
      text,
      timestamp: new Date().toLocaleTimeString('en-IN', { hour12: false }),
    };

    const nextHistory = [...messages, userMsg];
    setMessages(nextHistory);
    if (!textOverride) setInputText('');
    setChatLoading(true);
    setChatError('');

    if (selectedModel === 'pie-genai-platform') {
      try {
        const token = getStoredSessionToken();
        const resp = await fetch('/api/ai/platform-chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'x-pie-session': token } : {}),
          },
          body: JSON.stringify({ message: text }),
        });
        const data = await resp.json();
        if (!resp.ok) {
          setChatError(data.error || 'PIE GenAI Platform could not respond.');
          return;
        }

        const modelMsg: ExperienceChatMessage = {
          id: `m-${Date.now()}`,
          role: 'model',
          // Real synthesized text from the self-hosted platform, or its
          // own honest failure message (e.g. Ollama unreachable) — never
          // fabricated here.
          text: data.status === 'completed' ? data.reply : data.error || 'The PIE GenAI Platform run did not complete.',
          timestamp: new Date().toLocaleTimeString('en-IN', { hour12: false }),
          modelUsed: 'pie-genai-platform',
        };
        setMessages((prev) => [...prev, modelMsg]);
        onRefreshTelemetry?.();
      } catch {
        setChatError('Network error reaching /api/ai/platform-chat.');
      } finally {
        setChatLoading(false);
      }
      return;
    }

    try {
      const token = getStoredSessionToken();
      const resp = await fetch('/api/experience-chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'x-pie-session': token } : {}),
        },
        body: JSON.stringify({
          message: text,
          model: selectedModel,
          history: messages
            .filter((m) => m.id !== 'msg-welcome')
            .map((m) => ({ role: m.role, text: m.text })),
          userDeskContext: {
            activeIndex,
            riskProfile: session?.riskProfile ?? 'Conservative',
            adherencePct: psychology.ruleAdherencePct,
            mood: psychology.selfAssessmentMood,
          },
        }),
      });

      const data = await resp.json();
      if (!resp.ok) {
        setChatError(data.error || 'AI Experience Coach could not respond.');
        return;
      }

      const referenced = (Array.isArray(data.referencedStoryIds) ? data.referencedStoryIds : [])
        .map((id: string) => stories.find((s) => s.id === id))
        .filter((s: SuccessStory | undefined): s is SuccessStory => Boolean(s))
        .map((s: SuccessStory) => ({
          id: s.id,
          traderAlias: s.traderAlias,
          scope: s.scope,
        }));

      const modelMsg: ExperienceChatMessage = {
        id: `m-${Date.now()}`,
        role: 'model',
        text: data.reply || '',
        timestamp: new Date().toLocaleTimeString('en-IN', { hour12: false }),
        modelUsed: data.modelUsed ?? selectedModel,
        domain: data.domain ?? 'TRADING_INTELLIGENCE',
        toolsExecuted: Array.isArray(data.toolsExecuted) ? data.toolsExecuted : [],
        latencyMs: data.latencyMs,
        estimatedTokens: data.estimatedTokens,
        referencedStories: referenced,
        suggestedActions: Array.isArray(data.suggestedActions) ? data.suggestedActions : [],
        productImprovementCandidate: data.productImprovementCandidate
          ? { ...data.productImprovementCandidate, saved: false }
          : null,
      };

      setMessages((prev) => [...prev, modelMsg]);
      onRefreshTelemetry?.();
    } catch {
      setChatError('Network error reaching /api/experience-chat.');
    } finally {
      setChatLoading(false);
    }
  };

  const handleSaveCandidateFeedback = (
    msgId: string,
    candidate: NonNullable<ExperienceChatMessage['productImprovementCandidate']>
  ) => {
    if (candidate.saved) return;
    onAddFeedback({
      title: candidate.title,
      category: FEEDBACK_CATEGORIES.includes(candidate.category) ? candidate.category : 'Intelligence & AI',
      description: candidate.summary,
      source: 'AI Chat Synthesis',
    });
    setMessages((prev) =>
      prev.map((m) =>
        m.id === msgId && m.productImprovementCandidate
          ? { ...m, productImprovementCandidate: { ...m.productImprovementCandidate, saved: true } }
          : m
      )
    );
    setRightTab('roadmap');
  };

  const handleSubmitStory = (e: React.FormEvent) => {
    e.preventDefault();
    if (!storyForm.keyLesson.trim() || !storyForm.fullExperience.trim()) return;
    onAddSuccessStory({
      scope: 'Internal PIE Desk',
      traderAlias: storyForm.traderAlias.trim() || 'PIE Desk Trader',
      regionOrDesk: `NSE · ${storyForm.marketFocus}`,
      marketFocus: storyForm.marketFocus,
      strategyUsed: storyForm.strategyUsed,
      capitalDeployedLabel: storyForm.capitalDeployedLabel,
      riskPerTradePct: Number(storyForm.riskPerTradePct) || 1.0,
      winRatePct: Number(storyForm.winRatePct) || 55,
      maxDrawdownPct: Number(storyForm.maxDrawdownPct) || 3.0,
      netReturnLabel: storyForm.netReturnLabel,
      adherenceScore: Number(storyForm.adherenceScore) || 90,
      keyLesson: storyForm.keyLesson.trim(),
      fullExperience: storyForm.fullExperience.trim(),
    });
    setStoryForm((prev) => ({ ...prev, keyLesson: '', fullExperience: '' }));
    setShowStoryForm(false);
  };

  const handleSubmitFeedback = (e: React.FormEvent) => {
    e.preventDefault();
    if (!feedbackForm.title.trim() || !feedbackForm.description.trim()) return;
    onAddFeedback({
      title: feedbackForm.title.trim(),
      category: feedbackForm.category,
      description: feedbackForm.description.trim(),
      source: 'User Submission',
    });
    setFeedbackForm({ title: '', category: 'Execution & Risk', description: '' });
    setShowFeedbackForm(false);
  };

  const handleGenerateInsights = async () => {
    setInsightsLoading(true);
    setInsightsError('');
    try {
      const token = getStoredSessionToken();
      const resp = await fetch('/api/experience-insights', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'x-pie-session': token } : {}),
        },
        body: JSON.stringify({
          model: selectedModel,
        }),
      });
      const data = await resp.json();
      if (!resp.ok) {
        setInsightsError(data.error || 'Could not synthesize roadmap insights.');
      } else {
        setInsightsText(data.synthesis || '');
        onRefreshTelemetry?.();
      }
    } catch {
      setInsightsError('Network error reaching /api/experience-insights.');
    } finally {
      setInsightsLoading(false);
    }
  };

  const visibleStories = stories.filter((s) =>
    storyFilter === 'All' ? true : s.scope === storyFilter
  );

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
      {/* Left: Gen AI Experience & Benchmark Chat */}
      <div className="lg:col-span-7 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] flex flex-col overflow-hidden">
        <div className="p-4 sm:p-5 border-b border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-1.5 text-xs font-semibold text-sky-600 dark:text-sky-400">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Gen AI Experience Coach · Internal & Global Success Context</span>
            </div>
            <h2 className="font-display text-base font-bold text-slate-900 dark:text-white mt-0.5">
              Share Experience & Get Success-Story Guidance
            </h2>
            <p className="text-[11px] font-mono tabular-nums text-slate-500 dark:text-slate-400 mt-0.5">
              {activeIndex} · Authorized ₹{deploymentConfig.authorizedAllocationInr.toLocaleString('en-IN')} · Adherence {psychology.ruleAdherencePct}% · {stories.length} Benchmarks
            </p>
          </div>

          <div className="flex items-center gap-2">
            <label htmlFor="gemini-model-select" className="text-[11px] text-slate-500">
              Model
            </label>
            <select
              id="gemini-model-select"
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value as GeminiChatModel)}
              data-tip="Select which model powers your experience coaching: a Gemini model, or PIE's own self-hosted GenAI Platform (Ollama-backed)."
              className="min-h-[36px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] text-xs font-mono text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-sky-500 cursor-pointer"
            >
              <option value="gemini-4-hydrogen">gemini-4-hydrogen (Balanced)</option>
              <option value="gemini-3.1-flash-lite">gemini-3.1-flash-lite (Fast)</option>
              <option value="pie-genai-platform">PIE GenAI Platform (Self-Hosted)</option>
            </select>
          </div>
        </div>

        {/* Starter Experience Prompts */}
        <div className="px-4 sm:px-5 py-2.5 border-b border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#0B1120]/60 flex flex-wrap gap-2">
          {STARTER_PROMPTS.map((prompt) => (
            <button
              key={prompt}
              type="button"
              disabled={chatLoading}
              onClick={() => sendMessage(prompt)}
              className="text-left text-[11px] px-2.5 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] text-slate-600 dark:text-slate-300 hover:border-sky-500/50 hover:text-sky-600 dark:hover:text-sky-400 transition-colors cursor-pointer disabled:opacity-50"
            >
              {prompt}
            </button>
          ))}
        </div>

        {/* Conversation Feed */}
        <div className="p-4 sm:p-5 space-y-4 max-h-[520px] overflow-y-auto">
          {messages.map((msg) => {
            const isUser = msg.role === 'user';
            return (
              <div key={msg.id} className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}>
                <div
                  className={`max-w-[92%] rounded-xl px-4 py-3 text-xs sm:text-sm leading-relaxed ${
                    isUser
                      ? 'bg-sky-600 text-white'
                      : 'border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-[#0B1120] text-slate-800 dark:text-slate-200'
                  }`}
                >
                  <div className="flex items-center justify-between gap-4 text-[10px] opacity-80 mb-1 font-mono">
                    <span>
                      {isUser
                        ? session?.displayName ?? 'Trader'
                        : `${msg.domain === 'PRODUCT_INTELLIGENCE' ? 'Product Agent' : 'Trading Agent'} · ${msg.modelUsed ?? selectedModel}`}
                    </span>
                    <span>
                      {msg.latencyMs ? `${msg.latencyMs}ms · ` : ''}
                      {msg.estimatedTokens ? `${msg.estimatedTokens} tok · ` : ''}
                      {msg.timestamp}
                    </span>
                  </div>
                  <div className="whitespace-pre-line">{msg.text}</div>

                  {!isUser && msg.toolsExecuted && msg.toolsExecuted.length > 0 && (
                    <div className="mt-2.5 pt-2 border-t border-slate-200 dark:border-slate-800 text-[10px] font-mono text-slate-500 dark:text-slate-400">
                      Authoritative Tools Invoked:{' '}
                      {msg.toolsExecuted.map((t) => `${t.toolName} (${t.durationMs}ms)`).join(' · ')}
                    </div>
                  )}

                  {!isUser && msg.suggestedActions && msg.suggestedActions.length > 0 && (
                    <div className="mt-3 pt-2.5 border-t border-slate-200 dark:border-slate-800 space-y-1">
                      <div className="text-[11px] font-semibold text-sky-600 dark:text-sky-400">
                        Recommended Next Actions
                      </div>
                      <ul className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
                        {msg.suggestedActions.map((act) => (
                          <li key={act}>• {act}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {!isUser && msg.referencedStories && msg.referencedStories.length > 0 && (
                    <div className="mt-2.5 pt-2 border-t border-slate-200 dark:border-slate-800 flex flex-wrap items-center gap-1.5">
                      <span className="text-[11px] text-slate-500">Grounded in:</span>
                      {msg.referencedStories.map((ref) => (
                        <button
                          key={ref.id}
                          type="button"
                          onClick={() => {
                            setRightTab('stories');
                            setStoryFilter('All');
                            setHighlightedStoryId(ref.id);
                          }}
                          className="text-[11px] px-2 py-0.5 rounded border border-sky-500/30 bg-sky-500/8 text-sky-600 dark:text-sky-400 hover:bg-sky-500/15 cursor-pointer"
                        >
                          {ref.traderAlias} ({ref.scope === 'Global Benchmark' ? 'Global' : 'Internal'})
                        </button>
                      ))}
                    </div>
                  )}

                  {!isUser && msg.productImprovementCandidate && (
                    <div className="mt-3 p-3 rounded-lg border border-amber-500/30 bg-amber-500/8 text-xs space-y-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-semibold text-amber-700 dark:text-amber-300 flex items-center gap-1.5">
                          <Lightbulb className="w-3.5 h-3.5" />
                          Future Product Improvement Detected · {msg.productImprovementCandidate.category}
                        </span>
                      </div>
                      <div className="font-semibold text-slate-900 dark:text-white">
                        {msg.productImprovementCandidate.title}
                      </div>
                      <div className="text-slate-600 dark:text-slate-300">
                        {msg.productImprovementCandidate.summary}
                      </div>
                      <button
                        type="button"
                        disabled={msg.productImprovementCandidate.saved}
                        onClick={() =>
                          handleSaveCandidateFeedback(msg.id, msg.productImprovementCandidate!)
                        }
                        className="min-h-[34px] px-3 py-1 rounded-md bg-amber-600 hover:bg-amber-500 text-white font-semibold inline-flex items-center gap-1.5 disabled:opacity-60 cursor-pointer"
                      >
                        {msg.productImprovementCandidate.saved ? (
                          <>
                            <CheckCircle2 className="w-3.5 h-3.5" /> Added to Product Roadmap
                          </>
                        ) : (
                          <>
                            <Plus className="w-3.5 h-3.5" /> Add to Future Product Roadmap
                          </>
                        )}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}

          {chatLoading && (
            <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
              <Loader2 className="w-4 h-4 animate-spin text-sky-500" />
              <span>Comparing against internal & global success stories via {selectedModel}...</span>
            </div>
          )}

          {chatError && (
            <div className="p-3 rounded-lg border border-rose-500/40 bg-rose-500/10 text-xs text-rose-600 dark:text-rose-300">
              {chatError}
            </div>
          )}
        </div>

        {/* Composer */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sendMessage();
          }}
          className="p-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#0B1120]/60 space-y-2"
        >
          <div className="flex flex-col sm:flex-row gap-2">
            <textarea
              rows={2}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="Describe your trade experience, discipline challenge, or a feature idea to improve PIE Trader..."
              className="flex-1 px-3 py-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#111827] text-xs sm:text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-sky-500 resize-none"
            />
            <button
              type="submit"
              disabled={chatLoading || !inputText.trim()}
              data-tip="Send your experience or question to Gemini AI grounded in internal & global trader success stories."
              className="min-h-[44px] px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center justify-center gap-2 disabled:opacity-40 cursor-pointer shrink-0"
            >
              <Send className="w-3.5 h-3.5" />
              <span>Send</span>
            </button>
          </div>
        </form>
      </div>

      {/* Right: Internal & Global Success Stories + Future Product Roadmap */}
      <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
        <div className="p-2 border-b border-slate-200 dark:border-slate-800 grid grid-cols-3 gap-1 bg-slate-50 dark:bg-[#0B1120]">
          <button
            type="button"
            onClick={() => setRightTab('stories')}
            data-tip="Browse anonymized Internal PIE Desk & Global Benchmark trader success stories and lessons."
            className={`min-h-[38px] px-2 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1 cursor-pointer transition-colors ${
              rightTab === 'stories'
                ? 'bg-white dark:bg-[#111827] text-slate-900 dark:text-white shadow-xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Award className="w-3.5 h-3.5 text-sky-500 shrink-0" />
            <span className="truncate">Stories ({stories.length})</span>
          </button>
          <button
            type="button"
            onClick={() => setRightTab('roadmap')}
            data-tip="Review, verify tests, and approve Product Intelligence improvements isolated from live trading rules."
            className={`min-h-[38px] px-2 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1 cursor-pointer transition-colors ${
              rightTab === 'roadmap'
                ? 'bg-white dark:bg-[#111827] text-slate-900 dark:text-white shadow-xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Lightbulb className="w-3.5 h-3.5 text-amber-500 shrink-0" />
            <span className="truncate">Roadmap ({feedback.length})</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setRightTab('telemetry');
              onRefreshTelemetry?.();
            }}
            data-tip="Inspect live LLM Gateway benchmarks, PermissionEngine tool execution audit trail, and persisted Agent Tasks."
            className={`min-h-[38px] px-2 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1 cursor-pointer transition-colors ${
              rightTab === 'telemetry'
                ? 'bg-white dark:bg-[#111827] text-slate-900 dark:text-white shadow-xs'
                : 'text-slate-500 hover:text-slate-800 dark:hover:text-slate-200'
            }`}
          >
            <Cpu className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
            <span className="truncate">AI Audit</span>
          </button>
        </div>

        {rightTab === 'stories' && (
          <div className="p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-1 text-[11px]">
                {(['All', 'Internal PIE Desk', 'Global Benchmark'] as const).map((f) => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => setStoryFilter(f)}
                    data-tip={`Filter stories: ${f}`}
                    className={`min-h-[30px] px-2.5 rounded-md font-medium cursor-pointer ${
                      storyFilter === f
                        ? 'bg-sky-600 text-white dark:bg-sky-500/20 dark:text-sky-300'
                        : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800'
                    }`}
                  >
                    {f === 'Internal PIE Desk' ? 'Internal' : f === 'Global Benchmark' ? 'Global' : 'All'}
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={() => setShowStoryForm((v) => !v)}
                data-tip="Publish your own verified trade lesson so peers and the AI Coach can learn from it."
                className="min-h-[32px] px-2.5 rounded-md border border-sky-500/40 text-sky-600 dark:text-sky-400 text-xs font-semibold inline-flex items-center gap-1 cursor-pointer"
              >
                <MessageSquarePlus className="w-3.5 h-3.5" />
                <span>{showStoryForm ? 'Close' : 'Share Experience'}</span>
              </button>
            </div>

            {showStoryForm && (
              <form
                onSubmit={handleSubmitStory}
                className="p-3.5 rounded-xl border border-sky-500/30 bg-sky-500/5 space-y-2.5 text-xs"
              >
                <div className="font-semibold text-slate-900 dark:text-white">
                  Publish Verified Experience to Community Hub
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    placeholder="Trader Alias"
                    value={storyForm.traderAlias}
                    onChange={(e) => setStoryForm((p) => ({ ...p, traderAlias: e.target.value }))}
                    className="px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                  />
                  <input
                    type="text"
                    placeholder="Strategy Used"
                    value={storyForm.strategyUsed}
                    onChange={(e) => setStoryForm((p) => ({ ...p, strategyUsed: e.target.value }))}
                    className="px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                  />
                  <input
                    type="text"
                    placeholder="Net Return (e.g. +14%)"
                    value={storyForm.netReturnLabel}
                    onChange={(e) => setStoryForm((p) => ({ ...p, netReturnLabel: e.target.value }))}
                    className="px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120] font-mono"
                  />
                  <input
                    type="number"
                    placeholder="Win Rate %"
                    value={storyForm.winRatePct}
                    onChange={(e) => setStoryForm((p) => ({ ...p, winRatePct: Number(e.target.value) }))}
                    className="px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120] font-mono"
                  />
                </div>
                <input
                  type="text"
                  required
                  placeholder="Key Lesson (1 sentence takeaway for peers)"
                  value={storyForm.keyLesson}
                  onChange={(e) => setStoryForm((p) => ({ ...p, keyLesson: e.target.value }))}
                  className="w-full px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                />
                <textarea
                  rows={2}
                  required
                  placeholder="Full trading experience & what helped you succeed..."
                  value={storyForm.fullExperience}
                  onChange={(e) => setStoryForm((p) => ({ ...p, fullExperience: e.target.value }))}
                  className="w-full px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                />
                <button
                  type="submit"
                  className="min-h-[36px] px-3 py-1.5 rounded-md bg-sky-600 text-white font-semibold cursor-pointer"
                >
                  Publish Story
                </button>
              </form>
            )}

            <div className="space-y-2.5 max-h-[540px] overflow-y-auto pr-0.5">
              {visibleStories.map((story) => {
                const isHighlighted = highlightedStoryId === story.id;
                return (
                  <div
                    key={story.id}
                    onClick={() => setHighlightedStoryId(story.id)}
                    className={`p-3.5 rounded-xl border text-xs space-y-2 transition-colors cursor-pointer ${
                      isHighlighted
                        ? 'border-sky-500 bg-sky-500/8'
                        : 'border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#0B1120]/70 hover:border-slate-300 dark:hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-slate-900 dark:text-white flex items-center gap-1.5">
                        {story.scope === 'Global Benchmark' ? (
                          <Globe2 className="w-3.5 h-3.5 text-sky-500 shrink-0" />
                        ) : (
                          <Award className="w-3.5 h-3.5 text-sky-500 shrink-0" />
                        )}
                        {story.traderAlias}
                      </span>
                      <span className="font-mono font-semibold text-sky-600 dark:text-sky-400">
                        {story.netReturnLabel}
                      </span>
                    </div>

                    <div className="text-[11px] text-slate-500 dark:text-slate-400">
                      {story.scope} · {story.marketFocus} · {story.strategyUsed}
                    </div>

                    <div className="font-mono tabular-nums text-[11px] text-slate-700 dark:text-slate-300">
                      Win {story.winRatePct}% · Max DD {story.maxDrawdownPct}% · Risk/Trade {story.riskPerTradePct}% · Adherence {story.adherenceScore}%
                    </div>

                    <p className="text-slate-700 dark:text-slate-200 font-medium leading-snug">
                      “{story.keyLesson}”
                    </p>
                    <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-relaxed">
                      {story.fullExperience}
                    </p>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {rightTab === 'roadmap' && (
          <div className="p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <button
                type="button"
                disabled={insightsLoading}
                onClick={handleGenerateInsights}
                data-tip="Ask Gemini AI to analyze all success stories & user feedback to prioritize future product improvements."
                className="min-h-[36px] px-3 rounded-lg bg-sky-600 hover:bg-sky-500 text-white text-xs font-semibold inline-flex items-center gap-1.5 disabled:opacity-50 cursor-pointer"
              >
                {insightsLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
                <span>Synthesize Future Product Priorities</span>
              </button>

              <button
                type="button"
                onClick={() => setShowFeedbackForm((v) => !v)}
                data-tip="Log a new feature request or safety improvement directly into the future product backlog."
                className="min-h-[36px] px-2.5 rounded-lg border border-slate-300 dark:border-slate-700 text-xs font-semibold inline-flex items-center gap-1 cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Suggest Feature</span>
              </button>
            </div>

            {insightsError && (
              <div className="p-3 rounded-lg border border-rose-500/40 bg-rose-500/10 text-xs text-rose-600 dark:text-rose-300">
                {insightsError}
              </div>
            )}

            {insightsText && (
              <div className="p-3.5 rounded-xl border border-sky-500/30 bg-sky-500/5 text-xs text-slate-700 dark:text-slate-200 whitespace-pre-line leading-relaxed">
                {insightsText}
              </div>
            )}

            {showFeedbackForm && (
              <form
                onSubmit={handleSubmitFeedback}
                className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/5 space-y-2.5 text-xs"
              >
                <input
                  type="text"
                  required
                  placeholder="Feature or improvement title"
                  value={feedbackForm.title}
                  onChange={(e) => setFeedbackForm((p) => ({ ...p, title: e.target.value }))}
                  className="w-full px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                />
                <select
                  value={feedbackForm.category}
                  onChange={(e) =>
                    setFeedbackForm((p) => ({
                      ...p,
                      category: e.target.value as ProductFeedbackCategory,
                    }))
                  }
                  className="w-full px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                >
                  {FEEDBACK_CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
                <textarea
                  rows={2}
                  required
                  placeholder="How will this improve execution safety, speed, or clarity?"
                  value={feedbackForm.description}
                  onChange={(e) => setFeedbackForm((p) => ({ ...p, description: e.target.value }))}
                  className="w-full px-2.5 py-2 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#0B1120]"
                />
                <button
                  type="submit"
                  className="min-h-[36px] px-3 py-1.5 rounded-md bg-amber-600 text-white font-semibold cursor-pointer"
                >
                  Submit Improvement
                </button>
              </form>
            )}

            <div className="space-y-2.5 max-h-[500px] overflow-y-auto">
              {feedback.map((item) => (
                <div
                  key={item.id}
                  className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-[#0B1120]/70 text-xs space-y-1.5"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="font-semibold text-slate-900 dark:text-white">{item.title}</div>
                    <button
                      type="button"
                      onClick={() => onUpvoteFeedback(item.id)}
                      className="min-h-[30px] px-2.5 rounded-md border border-slate-300 dark:border-slate-700 inline-flex items-center gap-1 font-mono tabular-nums hover:border-sky-500 cursor-pointer shrink-0"
                    >
                      <ThumbsUp className="w-3 h-3 text-sky-500" />
                      <span>{item.upvotes}</span>
                    </button>
                  </div>
                  <div className="text-[11px] text-slate-500">
                    {item.category} · <span className="font-semibold text-slate-700 dark:text-slate-200">{item.status}</span> · {item.testsVerified ? 'Tests Verified' : 'Tests Pending'}
                  </div>
                  <p className="text-slate-600 dark:text-slate-300 leading-relaxed">{item.description}</p>
                  {onTransitionFeedback && (
                    <div className="pt-1.5 flex flex-wrap items-center gap-1.5">
                      {!item.testsVerified && (
                        <button
                          type="button"
                          onClick={() => onTransitionFeedback(item.id, 'Tests Verified', true)}
                          data-tip="Mark sandbox unit & integration checks verified before release approval."
                          className="px-2 py-1 rounded border border-sky-500/40 text-sky-600 dark:text-sky-400 text-[11px] font-medium hover:bg-sky-500/10 cursor-pointer"
                        >
                          Verify Tests
                        </button>
                      )}
                      {item.status !== 'Approved for Release' && (
                        <button
                          type="button"
                          onClick={() => onTransitionFeedback(item.id, 'Approved for Release', item.testsVerified)}
                          data-tip="Requires Tests Verified first. Persists human approval into immutable audit log."
                          className="px-2 py-1 rounded border border-emerald-500/40 text-emerald-600 dark:text-emerald-400 text-[11px] font-medium hover:bg-emerald-500/10 cursor-pointer"
                        >
                          Approve Release
                        </button>
                      )}
                      {item.status === 'Approved for Release' && (
                        <button
                          type="button"
                          onClick={() => onTransitionFeedback(item.id, 'Rolled Back', false)}
                          data-tip="Revert approved product change back to rolled-back audit state."
                          className="px-2 py-1 rounded border border-rose-500/40 text-rose-600 dark:text-rose-400 text-[11px] font-medium hover:bg-rose-500/10 cursor-pointer"
                        >
                          Rollback
                        </button>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {rightTab === 'telemetry' && (
          <div className="p-4 space-y-4 text-xs max-h-[580px] overflow-y-auto">
            <div className="p-3 rounded-lg border border-sky-500/30 bg-sky-500/5 space-y-1">
              <div className="font-semibold text-sky-600 dark:text-sky-400 flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5" />
                Domain Isolation & Persistence Active
              </div>
              <div className="text-[11px] text-slate-600 dark:text-slate-300">
                {telemetry?.storageEngine ?? 'Authoritative Server Store'} · {telemetry?.conversationsCount ?? 0} saved chat turns · {telemetry?.multiUserTenantCount ?? 1} isolated desk(s).
              </div>
            </div>

            <div className="space-y-2">
              <div className="font-semibold text-slate-900 dark:text-white">LLM Gateway Benchmarks</div>
              <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-lg">
                <table className="w-full text-[11px] font-mono tabular-nums">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 text-left">
                      <th className="py-2 px-2.5 font-sans">Model</th>
                      <th className="py-2 px-2 text-right">Calls</th>
                      <th className="py-2 px-2 text-right">Avg ms</th>
                      <th className="py-2 px-2 text-right">Tokens</th>
                      <th className="py-2 px-2.5 text-right">Cost (₹)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {(telemetry?.llmBenchmarks ?? []).map((b) => (
                      <tr key={b.model}>
                        <td className="py-2 px-2.5 font-semibold text-slate-800 dark:text-slate-200">{b.model}</td>
                        <td className="py-2 px-2 text-right">{b.calls}</td>
                        <td className="py-2 px-2 text-right">{b.avgLatencyMs}</td>
                        <td className="py-2 px-2 text-right">{b.totalTokens.toLocaleString('en-IN')}</td>
                        <td className="py-2 px-2.5 text-right">₹{b.totalCostInr.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="space-y-2">
              <div className="font-semibold text-slate-900 dark:text-white">Tool Registry Execution Audit</div>
              {(!telemetry || telemetry.toolExecutions.length === 0) ? (
                <p className="text-[11px] text-slate-500">Send a message in AI Experience Chat or synthesize roadmap insights to record live tool calls.</p>
              ) : (
                <div className="space-y-1.5">
                  {telemetry.toolExecutions.slice(0, 8).map((rec) => (
                    <div key={rec.id} className="p-2.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-[#0B1120]/60 space-y-0.5">
                      <div className="flex items-center justify-between font-mono text-[11px]">
                        <span className="font-semibold text-slate-900 dark:text-white">{rec.toolName}</span>
                        <span className={rec.permitted ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}>
                          {rec.permitted ? `ALLOWED · ${rec.durationMs}ms` : 'DENIED'}
                        </span>
                      </div>
                      <div className="text-[10px] text-slate-500">{rec.domain} · {rec.outputSummary}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
