export type IndexSymbol = 'NIFTY 50' | 'BANKNIFTY' | 'FINNIFTY' | 'MIDCPNIFTY' | 'SENSEX';

export type DecisionStatus =
  | 'STRONG OPPORTUNITY'
  | 'VALID OPPORTUNITY'
  | 'WAIT FOR CONFIRMATION'
  | 'NO TRADE'
  | 'RISK BLOCKED';

export type MarketRegime =
  | 'Trending Bullish'
  | 'Trending Bearish'
  | 'Range-Bound Compression'
  | 'High-Volatility Expansion'
  | 'Event Uncertainty';

export type RiskProfile = 'Conservative' | 'Moderate' | 'Aggressive';

export type PrimaryWorkspaceTab =
  | 'dashboard'
  | 'options'
  | 'intelligence'
  | 'basket'
  | 'capital'
  | 'brokers';

export type DashboardInsideTab = 'ranked' | 'index-focus' | 'sectors' | 'regime';
export type OptionsInsideTab = 'chain' | 'oi-profile' | 'iv-greeks';
export type IntelligenceInsideTab = 'pipeline' | 'news' | 'macro' | 'probability' | 'experience-ai';
export type BasketInsideTab = 'builder' | 'scaling' | 'positions';
export type CapitalInsideTab = 'deployment' | 'ledger' | 'risk' | 'psychology' | 'backtest';
export type BrokersInsideTab = 'adapters' | 'compliance' | 'backend-adr' | 'preferences';

export interface CandlePoint {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  vwap: number;
  volume: number;
}

export interface IndexMasterSpec {
  symbol: IndexSymbol;
  exchange: 'NSE' | 'BSE';
  instrumentToken: string;
  spot: number;
  prevClose: number;
  dayChange: number;
  dayChangePct: number;
  pdh: number;
  pdl: number;
  pwh: number;
  pwl: number;
  vwap: number;
  atr14: number;
  indiaVix: number;
  pcr: number;
  maxPain: number;
  lotSize: number;
  tickSize: number;
  strikeInterval: number;
  expiries: string[];
  tradingSession: string;
  regime: MarketRegime;
  fiiNetCr: number;
  diiNetCr: number;
  advances: number;
  declines: number;
  smcStructure: {
    daily: 'Bullish' | 'Bearish' | 'Neutral';
    h4: 'Bullish' | 'Bearish' | 'Neutral';
    h1: 'Bullish' | 'Bearish' | 'Neutral';
    m15: 'Bullish' | 'Bearish' | 'Neutral';
    m5: 'Bullish' | 'Bearish' | 'Neutral';
    m3: 'Bullish' | 'Bearish' | 'Neutral';
    activeSignal: string;
    fvgZone: [number, number];
    orderBlockZone: [number, number];
    liquidityTarget: number;
  };
  candles: CandlePoint[];
}

export interface OptionQuoteSide {
  tradingSymbol: string;
  instrumentToken: string;
  ltp: number;
  bid: number;
  ask: number;
  spreadPct: number;
  oi: number;
  chgOi: number;
  volume: number;
  iv: number;
  delta: number;
  gamma: number;
  theta: number;
  vega: number;
  liquidityScore: number;
  spreadValid: boolean;
}

export interface OptionChainRow {
  strike: number;
  isAtm: boolean;
  ce: OptionQuoteSide;
  pe: OptionQuoteSide;
}

export interface ProbabilityEstimate {
  probTarget1: number;
  probTarget2: number;
  probStopFirst: number;
  expectedHoldingMins: number;
  expectedNetRewardInr: number;
  expectedLossInr: number;
  expectedValueInr: number;
  expectedSharpe: number;
  sampleSize: number;
  lookbackPeriod: string;
  regimeConditioned: MarketRegime;
  uncertaintyBandPct: number;
  sufficientEvidence: boolean;
}

export interface TradeOpportunity {
  id: string;
  index: IndexSymbol;
  direction: 'Bullish CE' | 'Bearish PE' | 'Neutral / Flat';
  regime: MarketRegime;
  decision: DecisionStatus;
  setupName: string;
  signalTimestamp: string;
  validUntil: string;
  evidenceScore: number;
  suitabilityRank: number;
  contract: {
    strike: number;
    optionType: 'CE' | 'PE';
    expiry: string;
    tradingSymbol: string;
    instrumentToken: string;
    lotSize: number;
    recommendedLots: number;
    bid: number;
    ask: number;
    ltp: number;
    iv: number;
    delta: number;
    gamma: number;
    theta: number;
    vega: number;
    liquidityScore: number;
    spreadValid: boolean;
  };
  tradePlan: {
    entryRange: [number, number];
    entryTrigger: string;
    initialLots: number;
    initialStopLoss: number;
    target1: number;
    target2: number;
    target3: number;
    trailingMethod: string;
    invalidationCondition: string;
    expectedHoldingTime: string;
  };
  probability: ProbabilityEstimate;
  positiveEvidence: string[];
  negativeEvidence: string[];
  missingEvidence: string[];
  dimensionScores: {
    fundamental: number;
    technical: number;
    globalNews: number;
    sentiment: number;
    probability: number;
    riskFit: number;
    disciplineFit: number;
  };
}

export type BasketLegKind = 'Initial Entry' | 'Add-on 1' | 'Add-on 2' | 'Hedge Leg';
export type LegFillStatus = 'Pending' | 'Validated' | 'Filled' | 'Partial Fill' | 'Rejected' | 'Cancelled';

export interface BasketLeg {
  id: string;
  kind: BasketLegKind;
  index: IndexSymbol;
  tradingSymbol: string;
  strike: number;
  optionType: 'CE' | 'PE';
  expiry: string;
  side: 'BUY' | 'SELL';
  lots: number;
  lotSize: number;
  limitPrice: number;
  stopLoss: number;
  target: number;
  conditionNote: string;
  fillStatus: LegFillStatus;
  filledLots: number;
  avgFillPrice: number;
  rejectionReason?: string;
}

export interface ActivePosition {
  id: string;
  index: IndexSymbol;
  tradingSymbol: string;
  optionType: 'CE' | 'PE';
  expiry: string;
  strike: number;
  lotSize: number;
  totalLots: number;
  avgEntryPrice: number;
  currentLtp: number;
  stopLoss: number;
  target1: number;
  target2: number;
  trailingMethod: string;
  entryStages: {
    stage: string;
    lots: number;
    price: number;
    timestamp: string;
  }[];
  realizedPnlInr: number;
  mode: 'PAPER' | 'LIVE';
  openedAt: string;
}

export interface RiskLimits {
  profile: RiskProfile;
  maxRiskPerTradePct: number;
  maxRiskPerBasketPct: number;
  dailyLossLimitInr: number;
  weeklyLossLimitInr: number;
  maxDrawdownPct: number;
  maxTradesPerDay: number;
  consecutiveLossLimit: number;
  maxSpreadPct: number;
  minLiquidityScore: number;
  maxIndexExposurePct: number;
  eventRiskLock: boolean;
  behaviouralCooldownActive: boolean;
  killSwitchEngaged: boolean;
}

export interface CapitalLedger {
  depositsInr: number;
  withdrawalsInr: number;
  availableCapitalInr: number;
  reservedCapitalInr: number;
  deployedCapitalInr: number;
  realizedPnlInr: number;
  brokerageAndTaxesInr: number;
  peakEquityInr: number;
  dailyTradesCount: number;
  consecutiveLosses: number;
  weeklyPnlInr: number;
  monthlyReturnPct: number;
}

export interface NewsEvent {
  id: string;
  headline: string;
  source: string;
  publishedAt: string;
  eventTime?: string;
  affectedMarket: string;
  directionalImplication: 'Bullish India' | 'Bearish India' | 'Volatility Risk' | 'Neutral';
  impact: 'High' | 'Medium' | 'Low';
  relevanceConfidence: number;
  verified: boolean;
  alreadyPricedIn: boolean;
  isStaleOrDuplicate: boolean;
  confirmationNote: string;
}

export interface FundamentalMetric {
  id: string;
  label: string;
  value: string;
  delta: string;
  bias: 'Supportive' | 'Headwind' | 'Neutral';
  horizon: 'Macro / Positional' | 'Short-Term Context';
  note: string;
}

export interface SectorMetric {
  sector: string;
  changePct: number;
  relStrengthVsNifty: number;
  fiiFlowBias: 'Inflow' | 'Outflow' | 'Balanced';
  breadthPct: number;
  quadrant: 'Leading' | 'Improving' | 'Weakening' | 'Lagging';
}

export interface PsychologyLog {
  behaviouralRiskScore: number;
  ruleAdherencePct: number;
  plannedVsActualSlippagePts: number;
  overtradingCount: number;
  revengeTradeFlags: number;
  earlyExitCount: number;
  stopLossViolations: number;
  selfAssessmentMood: 'Calm & Objective' | 'Slightly Impatient' | 'FOMO / Chasing' | 'Fatigued';
  dailyReportText: string;
  cooldownMinutesRemaining: number;
}

export interface BrokerConnection {
  id: 'angel' | 'zerodha' | 'upstox' | 'dhan' | 'fyers';
  name: string;
  priority: number;
  authStatus: 'Connected' | 'Token Expired' | 'Disconnected';
  marketDataPerm: boolean;
  portfolioPerm: boolean;
  orderPlacementPerm: boolean;
  liveExecutionEnabled: boolean;
  staticIpVerified: boolean;
  twoFactorVerified: boolean;
  lastHeartbeat: string;
  rateLimitHeadroomPct: number;
}

export interface BacktestStrategySummary {
  id: string;
  name: string;
  index: IndexSymbol;
  regime: MarketRegime;
  split: 'In-Sample' | 'Validation' | 'Out-of-Sample';
  period: string;
  trades: number;
  winRatePct: number;
  profitFactor: number;
  expectedValueInr: number;
  maxDrawdownPct: number;
  sharpe: number;
  sortino: number;
  avgWinInr: number;
  avgLossInr: number;
  recoveryFactor: number;
  equityCurve: number[];
}

export interface JournalEntry {
  id: string;
  timestamp: string;
  index: IndexSymbol;
  tradingSymbol: string;
  setup: string;
  plannedEntry: number;
  actualEntry: number;
  plannedSl: number;
  actualSl: number;
  lots: number;
  netPnlInr: number;
  ruleFollowed: boolean;
  notes: string;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  actor: 'USER' | 'RISK_MANAGER' | 'TRADING_ORCHESTRATOR' | 'PROBABILITY_ENGINE';
  action: string;
  detail: string;
  outcome: 'APPROVED' | 'REJECTED' | 'INFO';
}

export type AuthProviderId = 'email' | 'google' | 'microsoft' | 'oidc';

export interface AuthProviderInfo {
  id: AuthProviderId;
  label: string;
  description: string;
  configured: boolean;
  callbackUrl?: string;
}

export interface UserSession {
  email: string;
  displayName: string;
  role: string;
  riskProfile: RiskProfile;
  mfaVerified: boolean;
  sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
  authProvider?: AuthProviderId;
  expiresAt?: number;
}

export interface DailyPnlSessionPoint {
  sessionIndex: number;
  dateLabel: string;
  dailyNetPnlInr: number;
  cumulativePnlInr: number;
  drawdownFromPeakInr: number;
  tradesCount: number;
}

export type AutomationMode =
  | 'Analysis Only'
  | 'Paper Automation'
  | 'Live Manual Approval'
  | 'Live Automated';

export interface StrategyAllocation {
  id: string;
  name: string;
  index: IndexSymbol | 'ALL';
  allocatedInr: number;
  deployedInr: number;
  reservedInr: number;
  maxCapitalPerTradeInr: number;
  maxRiskPerTradeInr: number;
  enabled: boolean;
  allowAddOns: boolean;
  explicitReallocationAllowed: boolean;
}

export interface CapitalDeploymentConfig {
  totalDeclaredCapitalInr: number;
  authorizedAllocationInr: number;
  maxCapitalPerStrategyInr: number;
  maxCapitalPerIndexInr: number;
  maxCapitalPerBasketInr: number;
  maxCapitalPerTradeInr: number;
  maxRiskPerTradeInr: number;
  dailyLossLimitInr: number;
  maxDailyExposureInr: number;
  maxOpenPositions: number;
  automationMode: AutomationMode;
  automationPaused: boolean;
  liveAutomationConsent: boolean;
  reinvestUnrealizedProfits: boolean;
}

export interface CapitalReservation {
  id: string;
  idempotencyKey: string;
  strategyId: string;
  tradingSymbol: string;
  index: IndexSymbol;
  requestedLots: number;
  filledLots: number;
  lotSize: number;
  reservedCapitalInr: number;
  reservedRiskInr: number;
  status: 'Reserved' | 'Partially Filled' | 'Filled' | 'Released' | 'Rejected';
  reason?: string;
  createdAt: string;
}

export interface LotSizingBreakdown {
  eligibleLots: number;
  riskPerLotInr: number;
  capitalPerLotInr: number;
  estimatedChargesPerLotInr: number;
  estimatedSlippagePerLotInr: number;
  riskBasedLots: number;
  capitalBasedLots: number;
  strategyBasedLots: number;
  indexExposureLots: number;
  brokerExchangeLots: number;
  portfolioRiskLots: number;
  limitingFactor: string;
  rejectedReason?: string;
}

// 'pie-genai-platform' routes to the real, self-hosted PIE GenAI Platform
// service (Ollama-backed) via /api/ai/platform-chat, not Gemini.
export type GeminiChatModel = 'gemini-4-hydrogen' | 'gemini-3.1-flash-lite' | 'pie-genai-platform';

export interface SuccessStory {
  id: string;
  scope: 'Internal PIE Desk' | 'Global Benchmark';
  traderAlias: string;
  regionOrDesk: string;
  marketFocus: string;
  strategyUsed: string;
  capitalDeployedLabel: string;
  riskPerTradePct: number;
  winRatePct: number;
  maxDrawdownPct: number;
  netReturnLabel: string;
  adherenceScore: number;
  keyLesson: string;
  fullExperience: string;
  verifiedByDesk: boolean;
  createdAt: string;
}

export type ProductFeedbackCategory =
  | 'Execution & Risk'
  | 'Capital & Sizing'
  | 'Intelligence & AI'
  | 'UX & Mobile';

export type ProductFeedbackStatus =
  | 'Under Review'
  | 'Engineering Proposed'
  | 'Tests Verified'
  | 'Approved for Release'
  | 'Rolled Back'
  | 'Planned Q1'
  | 'In Engineering';

export interface ProductFeedbackItem {
  id: string;
  title: string;
  category: ProductFeedbackCategory;
  description: string;
  submittedBy: string;
  source: 'AI Chat Synthesis' | 'User Submission';
  upvotes: number;
  status: ProductFeedbackStatus;
  createdAt: string;
  testsVerified?: boolean;
  approvedBy?: string;
  approvedAt?: string;
  versionTag?: string;
  auditTrail?: { timestamp: string; actor: string; action: string }[];
}

export type AgentDomain = 'TRADING_INTELLIGENCE' | 'PRODUCT_INTELLIGENCE';

export interface ToolExecutionRecord {
  id: string;
  timestamp: string;
  domain: AgentDomain;
  agentName: string;
  toolName: string;
  permitted: boolean;
  inputSummary: string;
  outputSummary: string;
  durationMs: number;
  reasonIfDenied?: string;
}

export interface AgentTaskRecord {
  id: string;
  createdAt: string;
  domain: AgentDomain;
  agentName: string;
  goal: string;
  status: 'Completed' | 'Blocked' | 'Fallback Used';
  modelUsed: string;
  latencyMs: number;
  estimatedTokens: number;
  estimatedCostInr: number;
  toolsInvoked: string[];
}

export interface LlmBenchmarkStats {
  model: string;
  provider: 'Google Gemini' | 'Gateway Fallback';
  calls: number;
  successes: number;
  fallbacks: number;
  avgLatencyMs: number;
  totalTokens: number;
  totalCostInr: number;
  toolAccuracyPct: number;
}

export interface ExperienceChatMessage {
  id: string;
  role: 'user' | 'model';
  text: string;
  timestamp: string;
  modelUsed?: GeminiChatModel;
  domain?: AgentDomain;
  toolsExecuted?: { toolName: string; durationMs: number; permitted: boolean }[];
  latencyMs?: number;
  estimatedTokens?: number;
  referencedStories?: { id: string; traderAlias: string; scope: SuccessStory['scope'] }[];
  suggestedActions?: string[];
  productImprovementCandidate?: {
    title: string;
    category: ProductFeedbackCategory;
    summary: string;
    saved?: boolean;
  } | null;
}

export interface BackendConnectionConfig {
  javaGatewayUrl: string;
  javaRiskEngineUrl: string;
  pythonAnalyticsUrl: string;
  preferExternalData: boolean;
}

export interface BackendServiceProbeResult {
  serviceId: 'express-desk' | 'java-gateway' | 'java-risk-engine' | 'python-analytics';
  label: string;
  url: string;
  reachable: boolean;
  statusCode?: number;
  latencyMs: number;
  runtimeStack: string;
  authStatus: string;
  contractVersion: string;
  detail: string;
  checkedAt: string;
}

export interface UserDeskState {
  userId: string;
  deploymentConfig: CapitalDeploymentConfig;
  strategies: StrategyAllocation[];
  ledger: CapitalLedger;
  reservations: CapitalReservation[];
  activePositions: ActivePosition[];
  basketLegs: BasketLeg[];
  limits: RiskLimits;
  psychology: PsychologyLog;
  brokers: BrokerConnection[];
  journal: JournalEntry[];
  auditLogs: AuditLogEntry[];
  watchlistIds: string[];
  updatedAt: string;
}

