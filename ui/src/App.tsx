import { useCallback, useEffect, useState } from 'react';
import {
  ActivePosition,
  AuditLogEntry,
  BackendConnectionConfig,
  BasketInsideTab,
  BasketLeg,
  BasketLegKind,
  BrokerConnection,
  BrokersInsideTab,
  CapitalDeploymentConfig,
  CapitalInsideTab,
  CapitalLedger,
  CapitalReservation,
  DashboardInsideTab,
  IndexSymbol,
  IntelligenceInsideTab,
  JournalEntry,
  OptionQuoteSide,
  OptionsInsideTab,
  PrimaryWorkspaceTab,
  ProductFeedbackCategory,
  ProductFeedbackItem,
  ProductFeedbackStatus,
  PsychologyLog,
  RiskLimits,
  RiskProfile,
  StrategyAllocation,
  SuccessStory,
  TradeOpportunity,
  UserDeskState,
  UserSession,
} from './types/pie';
import {
  BACKTEST_SUMMARIES,
  FUNDAMENTAL_METRICS,
  INDICES_MASTER,
  INITIAL_ACTIVE_POSITIONS,
  INITIAL_AUDIT_LOGS,
  INITIAL_BASKET_LEGS,
  INITIAL_BROKERS,
  INITIAL_CAPITAL_LEDGER,
  INITIAL_CAPITAL_RESERVATIONS,
  INITIAL_DEPLOYMENT_CONFIG,
  INITIAL_JOURNAL,
  INITIAL_NEWS_EVENTS,
  INITIAL_OPPORTUNITIES,
  INITIAL_PRODUCT_FEEDBACK,
  INITIAL_PSYCHOLOGY_LOG,
  INITIAL_RISK_LIMITS,
  INITIAL_STRATEGY_ALLOCATIONS,
  INITIAL_SUCCESS_STORIES,
  SECTOR_ROTATION_DATA,
} from './data/pieMarketData';
import { computeNetEquity, validateBasketWithRiskManager } from './engine/pieEngine';
import {
  AiTelemetrySnapshot,
  getStoredSessionToken,
  pieApi,
  setStoredSessionToken,
} from './services/pieApiClient';
import { LoginView } from './components/LoginView';
import { InsideTabOption, WorkspaceShell } from './components/WorkspaceShell';
import { DashboardView } from './components/views/DashboardView';
import { OptionChainView } from './components/views/OptionChainView';
import { IntelligenceView } from './components/views/IntelligenceView';
import { BasketExecutionView } from './components/views/BasketExecutionView';
import { CapitalRiskPsychView } from './components/views/CapitalRiskPsychView';
import { BrokersSettingsView } from './components/views/BrokersSettingsView';
import { GlobalTooltip } from './components/GlobalTooltip';

const DEFAULT_BACKEND_CONFIG: BackendConnectionConfig = {
  javaGatewayUrl: 'http://localhost:8080',
  javaRiskEngineUrl: 'http://localhost:8081',
  pythonAnalyticsUrl: 'http://localhost:8000',
  preferExternalData: false,
};

const UI_PREFS_KEY = 'pie_ui_preferences_v2';

const WORKSPACE_INSIDE_TABS: Record<PrimaryWorkspaceTab, InsideTabOption[]> = {
  dashboard: [
    {
      id: 'ranked',
      label: 'PIE Ranked',
      description: 'Opportunities ranked by 7-dimension evidence, net expected value, and RiskManager fit.',
    },
    {
      id: 'index-focus',
      label: 'Index Focus & Chart',
      description: 'Intraday candlestick structure with PDH/PDL, Fair Value Gaps, and multi-timeframe SMC bias.',
    },
    {
      id: 'sectors',
      label: 'Sector Rotation',
      description: 'Relative strength and institutional FII flow across NSE sectoral indices.',
    },
    {
      id: 'regime',
      label: 'Regime Matrix',
      description: 'Current volatility & trend classification across NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY & SENSEX.',
    },
  ],
  options: [
    {
      id: 'chain',
      label: 'Option Chain',
      description: 'Interactive strike matrix with bid/ask spread validation, OI, IV, Greeks, and 1-click basket staging.',
    },
    {
      id: 'oi-profile',
      label: 'OI & Max Pain',
      description: 'Visualizes Call vs Put Open Interest walls and Max Pain gravity levels for the chosen expiry.',
    },
    {
      id: 'iv-greeks',
      label: 'IV Skew & Greeks',
      description: 'D3 3D Implied Volatility surface across strikes & expiries plus full Greeks & liquidity quality table.',
    },
  ],
  intelligence: [
    {
      id: 'pipeline',
      label: '7D Decision Pipeline',
      description: '10-stage transparent gate audit showing Positive, Negative, and Missing evidence plus Gemini AI brief.',
    },
    {
      id: 'news',
      label: 'Global-to-India News',
      description: 'Macro & India event headlines filtered to isolate stale duplicates and highlight price-confirmation rules.',
    },
    {
      id: 'macro',
      label: 'Fundamental Macro',
      description: 'RBI policy, FII/DII flows, US yields, and crude context shaping positional directional bias.',
    },
    {
      id: 'probability',
      label: 'PIE Probability',
      description: 'Out-of-sample calibrated target/stop probabilities; blocks setups with sample count n < 30.',
    },
    {
      id: 'experience-ai',
      label: 'AI Experience Chat',
      description: 'Domain-separated Trading & Product GenAI agents backed by deterministic tools, persistence & telemetry.',
    },
  ],
  basket: [
    {
      id: 'builder',
      label: 'Basket Builder',
      description: 'Stage multi-leg option entries, validate against RiskManager, test partial fills, and save templates.',
    },
    {
      id: 'scaling',
      label: 'Position Scaling',
      description: 'Scale into profitable trades on verified BOS while strictly blocking averaging down on losers.',
    },
    {
      id: 'positions',
      label: 'Active Positions',
      description: 'Monitor open paper/live lots, unrealized MTM P&L, partial exits, or full position close.',
    },
  ],
  capital: [
    {
      id: 'deployment',
      label: 'Capital Deployment',
      description: 'Ring-fence authorized capital from undeployed funds, allocate per strategy, and run automatic lot sizing.',
    },
    {
      id: 'ledger',
      label: 'Capital Ledger',
      description: 'Track equity, fees, 30-session cumulative P&L curve, and export CSV audit logs.',
    },
    {
      id: 'risk',
      label: 'Risk Guardrails',
      description: 'Select Conservative/Moderate/Aggressive limits and toggle Event-Risk or Behavioural Cooldown locks.',
    },
    {
      id: 'psychology',
      label: 'Trader Psychology',
      description: 'Monitor rule adherence, SL violations, mood self-assessment, and voluntary cooldown protection.',
    },
    {
      id: 'backtest',
      label: 'Backtest & Journal',
      description: 'Compare In-Sample vs Validation vs Out-of-Sample equity curves and inspect executed trade journal.',
    },
  ],
  brokers: [
    {
      id: 'adapters',
      label: 'Broker Connections',
      description: 'Connect Angel, Zerodha, Upstox, Dhan, or FYERS with separated Data, Portfolio, and Order Auth gates.',
    },
    {
      id: 'compliance',
      label: 'Compliance & Audit',
      description: 'Confirm SEBI retail API checklist and inspect the immutable RiskManager & Orchestrator audit trail.',
    },
    {
      id: 'backend-adr',
      label: 'Backend & Architecture',
      description: 'Live probe for Express Desk, Java 21/25 Gateway & Risk Engine, Python FastAPI, ADR-001, and Section 26 audit.',
    },
    {
      id: 'preferences',
      label: 'Preferences',
      description: 'Configure desk display theme, session scope, and workspace defaults.',
    },
  ],
};

function readUiPreferences(): { isDark: boolean; backendConfig: BackendConnectionConfig } {
  try {
    const raw = localStorage.getItem(UI_PREFS_KEY);
    if (!raw) return { isDark: true, backendConfig: DEFAULT_BACKEND_CONFIG };
    const parsed = JSON.parse(raw) as {
      isDark?: boolean;
      backendConfig?: Partial<BackendConnectionConfig>;
    };
    return {
      isDark: typeof parsed.isDark === 'boolean' ? parsed.isDark : true,
      backendConfig: {
        ...DEFAULT_BACKEND_CONFIG,
        ...(parsed.backendConfig || {}),
      },
    };
  } catch {
    return { isDark: true, backendConfig: DEFAULT_BACKEND_CONFIG };
  }
}

export default function App() {
  const initialPrefs = readUiPreferences();
  const [isDark, setIsDark] = useState<boolean>(initialPrefs.isDark);
  const [backendConfig, setBackendConfig] = useState<BackendConnectionConfig>(initialPrefs.backendConfig);
  const [session, setSession] = useState<UserSession | null>(null);
  const [restoringSession, setRestoringSession] = useState<boolean>(() => Boolean(getStoredSessionToken()));

  const [activeTab, setActiveTab] = useState<PrimaryWorkspaceTab>('dashboard');
  const [activeIndex, setActiveIndex] = useState<IndexSymbol>('NIFTY 50');

  const [insideTabsState, setInsideTabsState] = useState<{
    dashboard: DashboardInsideTab;
    options: OptionsInsideTab;
    intelligence: IntelligenceInsideTab;
    basket: BasketInsideTab;
    capital: CapitalInsideTab;
    brokers: BrokersInsideTab;
  }>({
    dashboard: 'ranked',
    options: 'chain',
    intelligence: 'pipeline',
    basket: 'builder',
    capital: 'deployment',
    brokers: 'adapters',
  });

  // Authoritative state hydrated from backend store (/api/desk/*)
  const [ledger, setLedger] = useState<CapitalLedger>(INITIAL_CAPITAL_LEDGER);
  const [limits, setLimits] = useState<RiskLimits>(INITIAL_RISK_LIMITS);
  const [basketLegs, setBasketLegs] = useState<BasketLeg[]>(INITIAL_BASKET_LEGS);
  const [activePositions, setActivePositions] = useState<ActivePosition[]>(INITIAL_ACTIVE_POSITIONS);
  const [psychology, setPsychology] = useState<PsychologyLog>(INITIAL_PSYCHOLOGY_LOG);
  const [brokers, setBrokers] = useState<BrokerConnection[]>(INITIAL_BROKERS);
  const [journal, setJournal] = useState<JournalEntry[]>(INITIAL_JOURNAL);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>(INITIAL_AUDIT_LOGS);
  const [watchlistIds, setWatchlistIds] = useState<string[]>(['opp-nifty-24400ce']);
  const [deploymentConfig, setDeploymentConfig] = useState<CapitalDeploymentConfig>(INITIAL_DEPLOYMENT_CONFIG);
  const [strategies, setStrategies] = useState<StrategyAllocation[]>(INITIAL_STRATEGY_ALLOCATIONS);
  const [reservations, setReservations] = useState<CapitalReservation[]>(INITIAL_CAPITAL_RESERVATIONS);

  const [successStories, setSuccessStories] = useState<SuccessStory[]>(INITIAL_SUCCESS_STORIES);
  const [productFeedback, setProductFeedback] = useState<ProductFeedbackItem[]>(INITIAL_PRODUCT_FEEDBACK);
  const [telemetry, setTelemetry] = useState<AiTelemetrySnapshot | null>(null);
  const [bannerNotice, setBannerNotice] = useState<{ type: 'ok' | 'warn'; text: string } | null>(null);

  const applyDeskSnapshot = useCallback((desk: UserDeskState) => {
    setLedger(desk.ledger);
    setLimits(desk.limits);
    setDeploymentConfig(desk.deploymentConfig);
    setStrategies(desk.strategies);
    setReservations(desk.reservations);
    setBasketLegs(desk.basketLegs);
    setActivePositions(desk.activePositions);
    setPsychology(desk.psychology);
    setBrokers(desk.brokers);
    setJournal(desk.journal);
    setAuditLogs(desk.auditLogs);
    setWatchlistIds(desk.watchlistIds);
  }, []);

  const pushNotice = useCallback((type: 'ok' | 'warn', text: string) => {
    setBannerNotice({ type, text });
    setTimeout(() => {
      setBannerNotice((prev) => (prev?.text === text ? null : prev));
    }, 5500);
  }, []);

  const refreshDeskFromBackend = useCallback(async () => {
    try {
      const state = await pieApi.getDeskState();
      applyDeskSnapshot(state.desk);
      setSuccessStories(state.stories);
      setProductFeedback(state.productFeedback);
      setTelemetry(state.telemetry);
    } catch (err) {
      const status = (err as Error & { status?: number })?.status;
      if (status === 401) {
        setStoredSessionToken('');
        setSession(null);
      }
      pushNotice('warn', err instanceof Error ? err.message : 'Unable to sync authoritative desk state.');
    }
  }, [applyDeskSnapshot, pushNotice]);

  // Restore signed session on reload without storing financial balances in localStorage
  useEffect(() => {
    const token = getStoredSessionToken();
    if (!token) {
      setRestoringSession(false);
      return;
    }
    let active = true;
    (async () => {
      try {
        const resp = await fetch('/api/auth/session', {
          headers: { 'x-pie-session': token },
        });
        if (!resp.ok) {
          setStoredSessionToken('');
          return;
        }
        const data = (await resp.json()) as { session: UserSession };
        if (!active) return;
        setSession(data.session);
        await refreshDeskFromBackend();
      } catch {
        setStoredSessionToken('');
      } finally {
        if (active) setRestoringSession(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [refreshDeskFromBackend]);

  // Persist ONLY non-sensitive UI preferences in localStorage (Section 26.D compliant)
  useEffect(() => {
    try {
      localStorage.setItem(
        UI_PREFS_KEY,
        JSON.stringify({
          isDark,
          backendConfig,
        })
      );
      // Clean up legacy localStorage financial keys from earlier builds if present
      localStorage.removeItem('pie_deployment_config_v1');
      localStorage.removeItem('pie_strategy_alloc_v1');
      localStorage.removeItem('pie_success_stories_v1');
      localStorage.removeItem('pie_product_feedback_v1');
    } catch {
      // Ignore storage quota errors
    }
  }, [isDark, backendConfig]);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
  }, [isDark]);

  const handleLogin = async (userSession: UserSession, desk?: UserDeskState) => {
    setSession(userSession);
    if (desk) {
      applyDeskSnapshot(desk);
    }
    await refreshDeskFromBackend();
  };

  const handleLogout = async () => {
    await pieApi.logout();
    setSession(null);
  };

  const handleApiError = (err: unknown, fallbackMsg: string) => {
    const typed = err as Error & { desk?: UserDeskState };
    if (typed?.desk) {
      applyDeskSnapshot(typed.desk);
    }
    pushNotice('warn', typed instanceof Error ? typed.message : fallbackMsg);
  };

  const handleToggleKillSwitch = async () => {
    try {
      const res = await pieApi.toggleKillSwitch();
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
    } catch (err) {
      handleApiError(err, 'Failed to toggle Kill Switch.');
    }
  };

  const handleChangeRiskProfile = async (profile: RiskProfile) => {
    try {
      const res = await pieApi.updateLimits({ profile });
      applyDeskSnapshot(res.desk);
      pushNotice('ok', `RiskManager profile updated to ${profile}.`);
    } catch (err) {
      handleApiError(err, 'Failed to update RiskManager profile.');
    }
  };

  const syncBasketToBackend = (nextLegs: BasketLeg[]) => {
    setBasketLegs(nextLegs);
    pieApi
      .syncBasketLegs(nextLegs)
      .then((res) => applyDeskSnapshot(res.desk))
      .catch((err) => handleApiError(err, 'Failed to sync basket legs.'));
  };

  const handleSendOpportunityToBasket = (opp: TradeOpportunity) => {
    setActiveIndex(opp.index);
    const initialLeg: BasketLeg = {
      id: `leg-${Date.now()}-1`,
      kind: 'Initial Entry',
      index: opp.index,
      tradingSymbol: opp.contract.tradingSymbol,
      strike: opp.contract.strike,
      optionType: opp.contract.optionType,
      expiry: opp.contract.expiry,
      side: 'BUY',
      lots: Math.max(1, opp.tradePlan.initialLots),
      lotSize: opp.contract.lotSize,
      limitPrice: opp.contract.ltp,
      stopLoss: opp.tradePlan.initialStopLoss,
      target: opp.tradePlan.target1,
      conditionNote: opp.tradePlan.entryTrigger,
      fillStatus: 'Validated',
      filledLots: 0,
      avgFillPrice: 0,
    };

    const addonLeg: BasketLeg = {
      id: `leg-${Date.now()}-2`,
      kind: 'Add-on 1',
      index: opp.index,
      tradingSymbol: opp.contract.tradingSymbol,
      strike: opp.contract.strike,
      optionType: opp.contract.optionType,
      expiry: opp.contract.expiry,
      side: 'BUY',
      lots: 1,
      lotSize: opp.contract.lotSize,
      limitPrice: Number((opp.contract.ltp * 1.1).toFixed(1)),
      stopLoss: Number((opp.contract.ltp * 0.92).toFixed(1)),
      target: opp.tradePlan.target2,
      conditionNote: 'Fresh 5m BOS + volume confirmation required',
      fillStatus: 'Pending',
      filledLots: 0,
      avgFillPrice: 0,
    };

    syncBasketToBackend([initialLeg, addonLeg]);
    setActiveTab('basket');
    setInsideTabsState((prev) => ({ ...prev, basket: 'builder' }));
    pushNotice('ok', `Staged ${opp.contract.tradingSymbol} into Basket Builder.`);
  };

  const handleAddOptionLegToBasket = (params: {
    index: IndexSymbol;
    strike: number;
    optionType: 'CE' | 'PE';
    expiry: string;
    quote: OptionQuoteSide;
    lotSize: number;
  }) => {
    const newLeg: BasketLeg = {
      id: `leg-${Date.now()}`,
      kind: basketLegs.length === 0 ? 'Initial Entry' : 'Add-on 1',
      index: params.index,
      tradingSymbol: params.quote.tradingSymbol,
      strike: params.strike,
      optionType: params.optionType,
      expiry: params.expiry,
      side: 'BUY',
      lots: 1,
      lotSize: params.lotSize,
      limitPrice: params.quote.ltp,
      stopLoss: Number((params.quote.ltp * 0.8).toFixed(1)),
      target: Number((params.quote.ltp * 1.28).toFixed(1)),
      conditionNote: `Option Chain selection (Spread ${params.quote.spreadPct}%, Liq ${params.quote.liquidityScore})`,
      fillStatus: params.quote.spreadValid ? 'Validated' : 'Pending',
      filledLots: 0,
      avgFillPrice: 0,
    };
    syncBasketToBackend([...basketLegs, newLeg]);
    pushNotice('ok', `Added ${params.quote.tradingSymbol} to Basket Builder.`);
  };

  const handlePaperExecuteOpportunity = async (opp: TradeOpportunity) => {
    if (session?.sessionMode === 'READ_ONLY_LIVE') {
      pushNotice('warn', 'Session is in Read-Only Intelligence mode. Switch to Paper Desk to simulate orders.');
      return;
    }
    try {
      const res = await pieApi.executePaperOpportunity(opp);
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
    } catch (err) {
      handleApiError(err, 'Paper order execution rejected.');
    }
  };

  const handleSubmitBasketPaper = async () => {
    if (session?.sessionMode === 'READ_ONLY_LIVE') {
      pushNotice('warn', 'Read-Only Intelligence mode blocks order simulation.');
      return;
    }
    try {
      const res = await pieApi.executeBasketPaper(basketLegs);
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
      setInsideTabsState((prev) => ({ ...prev, basket: 'positions' }));
    } catch (err) {
      handleApiError(err, 'Basket execution rejected.');
    }
  };

  const handleScaleInPosition = async (posId: string, addLots: number, confirmedBreakout: boolean) => {
    try {
      const res = await pieApi.scalePosition(posId, addLots, confirmedBreakout);
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
    } catch (err) {
      handleApiError(err, 'Scale-in rejected by RiskManager.');
    }
  };

  const handlePartialExitPosition = async (posId: string, exitLots: number) => {
    try {
      const res = await pieApi.exitPosition(posId, exitLots);
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
    } catch (err) {
      handleApiError(err, 'Position exit failed.');
    }
  };

  const handleEmergencySquareOffAll = async () => {
    try {
      const res = await pieApi.emergencySquareOffAll();
      applyDeskSnapshot(res.desk);
      pushNotice(res.notice.type, res.notice.text);
    } catch (err) {
      handleApiError(err, 'Emergency Square-Off failed.');
    }
  };

  if (restoringSession) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-[#0B1120] text-slate-600 dark:text-slate-300 text-xs font-mono">
        Verifying authoritative PIE Trader desk session...
      </div>
    );
  }

  if (!session) {
    return (
      <>
        <GlobalTooltip />
        <LoginView
          onLogin={handleLogin}
          isDark={isDark}
          onToggleTheme={() => setIsDark((d) => !d)}
        />
      </>
    );
  }

  const equityStats = computeNetEquity(ledger, activePositions);
  const totalSessionPnl = Number((ledger.realizedPnlInr + equityStats.unrealizedPnlInr).toFixed(2));
  const basketValidation = validateBasketWithRiskManager({
    legs: basketLegs,
    ledger,
    limits,
    activePositions,
  });

  const currentInsideTab = insideTabsState[activeTab];
  const handleSelectInsideTab = (id: string) => {
    setInsideTabsState((prev) => ({ ...prev, [activeTab]: id }));
  };

  return (
    <>
      <GlobalTooltip />
      <WorkspaceShell
        session={session}
        onLogout={handleLogout}
        isDark={isDark}
        onToggleTheme={() => setIsDark((d) => !d)}
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        insideTabs={WORKSPACE_INSIDE_TABS[activeTab]}
        activeInsideTab={currentInsideTab}
        onSelectInsideTab={handleSelectInsideTab}
        activeIndex={activeIndex}
        onSelectIndex={setActiveIndex}
        indices={INDICES_MASTER}
        netEquityInr={equityStats.netEquityInr}
        totalPnlInr={totalSessionPnl}
        remainingRiskBudgetInr={basketValidation.remainingRiskBudgetInr}
        limits={limits}
        onToggleKillSwitch={handleToggleKillSwitch}
        onOpenExperienceChat={() => {
          setActiveTab('intelligence');
          setInsideTabsState((prev) => ({ ...prev, intelligence: 'experience-ai' }));
        }}
        isExperienceChatActive={activeTab === 'intelligence' && insideTabsState.intelligence === 'experience-ai'}
      >
        {bannerNotice && (
          <div
            className={`mb-4 px-4 py-3 rounded-xl border text-xs font-medium flex items-center justify-between gap-3 ${
              bannerNotice.type === 'warn'
                ? 'border-rose-500/40 bg-rose-500/10 text-rose-700 dark:text-rose-300'
                : 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
            }`}
          >
            <span>{bannerNotice.text}</span>
            <button
              type="button"
              onClick={() => setBannerNotice(null)}
              className="underline shrink-0 cursor-pointer"
            >
              Dismiss
            </button>
          </div>
        )}

        {activeTab === 'dashboard' && (
          <DashboardView
            insideTab={insideTabsState.dashboard}
            onChangeInsideTab={(t) => setInsideTabsState((p) => ({ ...p, dashboard: t }))}
            activeIndex={activeIndex}
            onSelectIndex={setActiveIndex}
            indices={INDICES_MASTER}
            opportunities={INITIAL_OPPORTUNITIES}
            sectors={SECTOR_ROTATION_DATA}
            ledger={ledger}
            limits={limits}
            watchlistIds={watchlistIds}
            onToggleWatchlist={async (id) => {
              try {
                const res = await pieApi.toggleWatchlist(id);
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Watchlist update failed.');
              }
            }}
            onSendToBasket={handleSendOpportunityToBasket}
            onPaperExecute={handlePaperExecuteOpportunity}
            onInspectPipeline={(idx) => {
              setActiveIndex(idx);
              setActiveTab('intelligence');
              setInsideTabsState((p) => ({ ...p, intelligence: 'pipeline' }));
            }}
          />
        )}

        {activeTab === 'options' && (
          <OptionChainView
            insideTab={insideTabsState.options}
            activeIndex={activeIndex}
            onSelectIndex={setActiveIndex}
            indices={INDICES_MASTER}
            onAddOptionLegToBasket={handleAddOptionLegToBasket}
          />
        )}

        {activeTab === 'intelligence' && (
          <IntelligenceView
            insideTab={insideTabsState.intelligence}
            activeIndex={activeIndex}
            indices={INDICES_MASTER}
            opportunities={INITIAL_OPPORTUNITIES}
            newsEvents={INITIAL_NEWS_EVENTS}
            fundamentals={FUNDAMENTAL_METRICS}
            session={session}
            deploymentConfig={deploymentConfig}
            strategies={strategies}
            psychology={psychology}
            stories={successStories}
            onAddSuccessStory={async (story) => {
              try {
                const res = await pieApi.addSuccessStory(story);
                setSuccessStories(res.stories);
                await refreshDeskFromBackend();
                pushNotice('ok', 'Your anonymized trading experience was persisted to the Success Hub.');
              } catch (err) {
                handleApiError(err, 'Could not publish experience story.');
              }
            }}
            feedback={productFeedback}
            onAddFeedback={async (item: {
              title: string;
              category: ProductFeedbackCategory;
              description: string;
              source: ProductFeedbackItem['source'];
            }) => {
              try {
                const res = await pieApi.addProductFeedback({
                  ...item,
                  submittedBy: session.displayName,
                });
                setProductFeedback(res.productFeedback);
                await refreshDeskFromBackend();
                pushNotice('ok', 'Added improvement idea to governed Product Intelligence backlog.');
              } catch (err) {
                handleApiError(err, 'Could not add product feedback.');
              }
            }}
            onUpvoteFeedback={async (id) => {
              try {
                const res = await pieApi.upvoteProductFeedback(id);
                setProductFeedback(res.productFeedback);
              } catch (err) {
                handleApiError(err, 'Upvote failed.');
              }
            }}
            onTransitionFeedback={async (id, nextStatus: ProductFeedbackStatus, testsVerified?: boolean) => {
              try {
                const res = await pieApi.transitionProductFeedback(id, {
                  nextStatus,
                  testsVerified,
                  note: `Transitioned to ${nextStatus} by ${session.displayName}.`,
                });
                setProductFeedback(res.productFeedback);
                await refreshDeskFromBackend();
                pushNotice('ok', `Product item updated to ${nextStatus}.`);
              } catch (err) {
                handleApiError(err, 'Governance transition rejected.');
              }
            }}
            telemetry={telemetry}
            onRefreshTelemetry={async () => {
              try {
                const t = await pieApi.getAiTelemetry();
                setTelemetry(t);
              } catch (err) {
                handleApiError(err, 'Unable to load AI gateway telemetry.');
              }
            }}
          />
        )}

        {activeTab === 'basket' && (
          <BasketExecutionView
            insideTab={insideTabsState.basket}
            onChangeInsideTab={(t) => setInsideTabsState((p) => ({ ...p, basket: t }))}
            activeIndex={activeIndex}
            indices={INDICES_MASTER}
            basketLegs={basketLegs}
            onUpdateLeg={(id, patch) => {
              syncBasketToBackend(basketLegs.map((l) => (l.id === id ? { ...l, ...patch } : l)));
            }}
            onRemoveLeg={(id) => {
              syncBasketToBackend(basketLegs.filter((l) => l.id !== id));
            }}
            onAddStagedLeg={(kind: BasketLegKind) => {
              const spec = INDICES_MASTER[activeIndex];
              const atm = Math.round(spec.spot / spec.strikeInterval) * spec.strikeInterval;
              const shortSym = spec.symbol === 'NIFTY 50' ? 'NIFTY' : spec.symbol;
              const newLeg: BasketLeg = {
                id: `leg-${Date.now()}`,
                kind,
                index: activeIndex,
                tradingSymbol: `${shortSym}${spec.expiries[0].replace(/\s+/g, '').toUpperCase()}${atm}CE`,
                strike: atm,
                optionType: 'CE',
                expiry: spec.expiries[0],
                side: 'BUY',
                lots: 1,
                lotSize: spec.lotSize,
                limitPrice: 145,
                stopLoss: 118,
                target: 185,
                conditionNote: `${kind} staged with independent RiskManager budget check`,
                fillStatus: 'Pending',
                filledLots: 0,
                avgFillPrice: 0,
              };
              syncBasketToBackend([...basketLegs, newLeg]);
            }}
            onSubmitBasketPaper={handleSubmitBasketPaper}
            onSimulatePartialFill={(id) => {
              const nextLegs = basketLegs.map((l) =>
                l.id === id
                  ? {
                      ...l,
                      fillStatus: 'Partial Fill' as const,
                      filledLots: Math.max(1, Math.floor(l.lots / 2)),
                      avgFillPrice: l.limitPrice,
                    }
                  : l
              );
              syncBasketToBackend(nextLegs);
              pushNotice('ok', 'Simulated partial fill on leg; remaining quantity remains tracked.');
            }}
            onCancelPendingLegs={() => {
              const nextLegs = basketLegs.map((l) =>
                l.fillStatus === 'Pending' ? { ...l, fillStatus: 'Cancelled' as const } : l
              );
              syncBasketToBackend(nextLegs);
              pushNotice('ok', 'Cancelled remaining pending legs.');
            }}
            activePositions={activePositions}
            onScaleInPosition={handleScaleInPosition}
            onPartialExitPosition={handlePartialExitPosition}
            onAdjustPositionLtp={async (posId, delta) => {
              try {
                const res = await pieApi.adjustPositionLtp(posId, delta);
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Tick update failed.');
              }
            }}
            onEmergencySquareOffAll={handleEmergencySquareOffAll}
            ledger={ledger}
            limits={limits}
          />
        )}

        {activeTab === 'capital' && (
          <CapitalRiskPsychView
            insideTab={insideTabsState.capital}
            ledger={ledger}
            activePositions={activePositions}
            limits={limits}
            onChangeRiskProfile={handleChangeRiskProfile}
            onUpdateLimits={async (patch) => {
              try {
                const res = await pieApi.updateLimits({ patch });
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Failed to update RiskManager limits.');
              }
            }}
            psychology={psychology}
            onUpdateMood={async (mood) => {
              try {
                const res = await pieApi.updateMood(mood);
                applyDeskSnapshot(res.desk);
                if (res.notice) {
                  pushNotice(res.notice.type, res.notice.text);
                }
              } catch (err) {
                handleApiError(err, 'Mood update failed.');
              }
            }}
            onToggleCooldownLock={async () => {
              try {
                const res = await pieApi.updateLimits({
                  patch: { behaviouralCooldownActive: !limits.behaviouralCooldownActive },
                });
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Failed to toggle Cooldown Lock.');
              }
            }}
            backtests={BACKTEST_SUMMARIES}
            journal={journal}
            deploymentConfig={deploymentConfig}
            onUpdateDeploymentConfig={async (patch) => {
              try {
                const res = await pieApi.updateDeploymentConfig(patch);
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Capital config update rejected.');
              }
            }}
            onAdjustAuthorizedAllocation={async (nextAmountInr) => {
              try {
                const res = await pieApi.adjustAuthorizedAllocation(nextAmountInr);
                applyDeskSnapshot(res.desk);
                pushNotice(res.notice.type, res.notice.text);
              } catch (err) {
                handleApiError(err, 'Authorized allocation adjustment rejected.');
              }
            }}
            strategies={strategies}
            onUpdateStrategy={async (id, patch) => {
              try {
                const res = await pieApi.updateStrategy(id, patch);
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Strategy update rejected.');
              }
            }}
            reservations={reservations}
            brokers={brokers}
            opportunities={INITIAL_OPPORTUNITIES}
            indices={INDICES_MASTER}
            onRunAutomatedCycle={async () => {
              try {
                const res = await pieApi.runAutomatedCycle();
                applyDeskSnapshot(res.desk);
                pushNotice(res.notice.type, res.notice.text);
              } catch (err) {
                handleApiError(err, 'Automated cycle blocked by RiskManager.');
              }
            }}
            onConfirmReservationFill={async (reservationId, partialRatio = 1) => {
              try {
                const res = await pieApi.confirmReservationFill(reservationId, partialRatio);
                applyDeskSnapshot(res.desk);
                pushNotice(res.notice.type, res.notice.text);
              } catch (err) {
                handleApiError(err, 'Reservation reconciliation failed.');
              }
            }}
            onReleaseReservation={async (reservationId) => {
              try {
                const res = await pieApi.releaseReservation(reservationId);
                applyDeskSnapshot(res.desk);
                pushNotice(res.notice.type, res.notice.text);
              } catch (err) {
                handleApiError(err, 'Failed to release reservation.');
              }
            }}
            onToggleEmergencyStop={handleToggleKillSwitch}
          />
        )}

        {activeTab === 'brokers' && (
          <BrokersSettingsView
            insideTab={insideTabsState.brokers}
            brokers={brokers}
            onToggleBrokerConnection={async (id) => {
              try {
                const res = await pieApi.toggleBroker({ id, action: 'connection' });
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Broker connection toggle failed.');
              }
            }}
            onToggleBrokerPermission={async (id, perm) => {
              try {
                const res = await pieApi.toggleBroker({ id, action: 'permission', perm });
                applyDeskSnapshot(res.desk);
              } catch (err) {
                handleApiError(err, 'Broker permission update failed.');
              }
            }}
            auditLogs={auditLogs}
            session={session}
            isDark={isDark}
            onToggleTheme={() => setIsDark((d) => !d)}
            activeIndex={activeIndex}
            backendConfig={backendConfig}
            onUpdateBackendConfig={(patch) =>
              setBackendConfig((prev) => ({ ...prev, ...patch }))
            }
          />
        )}
      </WorkspaceShell>
    </>
  );
}
