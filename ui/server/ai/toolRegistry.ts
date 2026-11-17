import {
  AgentDomain,
  IndexSymbol,
  ProductFeedbackCategory,
  ToolExecutionRecord,
} from '../../src/types/pie';
import { INDICES_MASTER, INITIAL_OPPORTUNITIES } from '../../src/data/pieMarketData';
import {
  calculateStrategyLotSize,
  getCapitalDeploymentSummary,
} from '../../src/engine/capitalAllocationEngine';
import { authoritativeDb } from '../store/authoritativeDb';

export type RegisteredToolName =
  | 'get_authoritative_capital_summary'
  | 'calculate_validated_lot_size'
  | 'get_market_and_option_snapshot'
  | 'get_collective_benchmarks'
  | 'list_product_improvement_backlog'
  | 'record_product_improvement_task';

interface ToolDefinition {
  name: RegisteredToolName;
  allowedDomains: AgentDomain[];
  mutatesProductionTradingRules: boolean;
  description: string;
}

const TOOL_CATALOG: Record<RegisteredToolName, ToolDefinition> = {
  get_authoritative_capital_summary: {
    name: 'get_authoritative_capital_summary',
    allowedDomains: ['TRADING_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Reads user-scoped ring-fenced total, authorized, undeployed, and available capital.',
  },
  calculate_validated_lot_size: {
    name: 'calculate_validated_lot_size',
    allowedDomains: ['TRADING_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Runs deterministic PositionSizingEngine to calculate eligible lots under RiskManager caps.',
  },
  get_market_and_option_snapshot: {
    name: 'get_market_and_option_snapshot',
    allowedDomains: ['TRADING_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Reads verified spot, India VIX, PCR, regime, and primary option setup for an index.',
  },
  get_collective_benchmarks: {
    name: 'get_collective_benchmarks',
    allowedDomains: ['TRADING_INTELLIGENCE', 'PRODUCT_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Reads anonymized, privacy-preserving Internal PIE Desk and Global Benchmark cohorts.',
  },
  list_product_improvement_backlog: {
    name: 'list_product_improvement_backlog',
    allowedDomains: ['PRODUCT_INTELLIGENCE', 'TRADING_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Reads persisted product improvement items and approval states.',
  },
  record_product_improvement_task: {
    name: 'record_product_improvement_task',
    allowedDomains: ['PRODUCT_INTELLIGENCE'],
    mutatesProductionTradingRules: false,
    description: 'Creates an Under Review product improvement proposal isolated from live trading rules.',
  },
};

export class PermissionEngine {
  public authorize(domain: AgentDomain, toolName: RegisteredToolName): { ok: boolean; reason?: string } {
    const def = TOOL_CATALOG[toolName];
    if (!def) {
      return { ok: false, reason: `Unknown tool "${toolName}" rejected by ToolRegistry.` };
    }
    if (!def.allowedDomains.includes(domain)) {
      return {
        ok: false,
        reason: `Domain isolation violation: ${domain} is not permitted to invoke ${toolName}.`,
      };
    }
    return { ok: true };
  }
}

export class ToolRegistry {
  private readonly permissions = new PermissionEngine();

  public async executeTool<TInput extends Record<string, unknown>, TOutput>(params: {
    domain: AgentDomain;
    agentName: string;
    userId: string;
    toolName: RegisteredToolName;
    input: TInput;
  }): Promise<{ ok: boolean; data?: TOutput; record: ToolExecutionRecord }> {
    const start = Date.now();
    const auth = this.permissions.authorize(params.domain, params.toolName);

    if (!auth.ok) {
      const deniedRecord: ToolExecutionRecord = {
        id: `tool-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        timestamp: new Date().toISOString(),
        domain: params.domain,
        agentName: params.agentName,
        toolName: params.toolName,
        permitted: false,
        inputSummary: JSON.stringify(params.input).slice(0, 120),
        outputSummary: auth.reason ?? 'Permission denied',
        durationMs: Date.now() - start,
        reasonIfDenied: auth.reason,
      };
      authoritativeDb.recordToolExecution(deniedRecord);
      return { ok: false, record: deniedRecord };
    }

    let output: unknown;
    let outputSummary = '';

    switch (params.toolName) {
      case 'get_authoritative_capital_summary': {
        const desk = authoritativeDb.getOrCreateUserDesk(params.userId);
        const summary = getCapitalDeploymentSummary({
          config: desk.deploymentConfig,
          strategies: desk.strategies,
          reservations: desk.reservations,
          activePositions: desk.activePositions,
          ledger: desk.ledger,
        });
        output = {
          totalDeclaredCapitalInr: summary.totalDeclaredCapitalInr,
          authorizedAllocationInr: summary.authorizedAllocationInr,
          undeployedNotAuthorizedInr: summary.undeployedNotAuthorizedInr,
          availableAllocationInr: summary.availableAllocationInr,
          remainingDailyRiskInr: summary.remainingDailyRiskInr,
          killSwitchEngaged: desk.limits.killSwitchEngaged,
          automationMode: desk.deploymentConfig.automationMode,
          enabledStrategies: desk.strategies.filter((s) => s.enabled).map((s) => ({
            name: s.name,
            allocatedInr: s.allocatedInr,
            deployedInr: s.deployedInr,
          })),
        };
        outputSummary = `Authorized ₹${summary.authorizedAllocationInr.toLocaleString('en-IN')}, Available ₹${summary.availableAllocationInr.toLocaleString('en-IN')}, Risk Left ₹${summary.remainingDailyRiskInr.toLocaleString('en-IN')}`;
        break;
      }

      case 'calculate_validated_lot_size': {
        const idx = (params.input.index as IndexSymbol) || 'NIFTY 50';
        const desk = authoritativeDb.getOrCreateUserDesk(params.userId);
        const summary = getCapitalDeploymentSummary({
          config: desk.deploymentConfig,
          strategies: desk.strategies,
          reservations: desk.reservations,
          activePositions: desk.activePositions,
          ledger: desk.ledger,
        });
        const opp = INITIAL_OPPORTUNITIES.find((o) => o.index === idx) ?? INITIAL_OPPORTUNITIES[0];
        const strat =
          desk.strategies.find((s) => s.enabled && (s.index === idx || s.index === 'ALL')) ??
          desk.strategies[0];
        const sizing = calculateStrategyLotSize({
          opportunity: opp,
          strategy: strat,
          config: desk.deploymentConfig,
          summary,
          activePositions: desk.activePositions,
          limits: desk.limits,
        });
        output = {
          index: opp.index,
          contract: opp.contract.tradingSymbol,
          strategy: strat.name,
          eligibleLots: sizing.eligibleLots,
          limitingFactor: sizing.limitingFactor,
          riskPerLotInr: sizing.riskPerLotInr,
          capitalPerLotInr: sizing.capitalPerLotInr,
          rejectedReason: sizing.rejectedReason,
        };
        outputSummary = `${opp.contract.tradingSymbol}: ${sizing.eligibleLots} lots (${sizing.limitingFactor})`;
        break;
      }

      case 'get_market_and_option_snapshot': {
        const idx = (params.input.index as IndexSymbol) || 'NIFTY 50';
        const spec = INDICES_MASTER[idx] ?? INDICES_MASTER['NIFTY 50'];
        const opp = INITIAL_OPPORTUNITIES.find((o) => o.index === idx) ?? INITIAL_OPPORTUNITIES[0];
        output = {
          symbol: spec.symbol,
          spot: spec.spot,
          indiaVix: spec.indiaVix,
          pcr: spec.pcr,
          regime: spec.regime,
          maxPain: spec.maxPain,
          topSetup: opp.setupName,
          decision: opp.decision,
          probTarget1: opp.probability.probTarget1,
          sampleSize: opp.probability.sampleSize,
        };
        outputSummary = `${spec.symbol} Spot ${spec.spot} · VIX ${spec.indiaVix} · ${spec.regime}`;
        break;
      }

      case 'get_collective_benchmarks': {
        const stories = authoritativeDb.getSuccessStories().slice(0, 14).map((s) => ({
          id: s.id,
          scope: s.scope,
          traderAlias: s.traderAlias,
          marketFocus: s.marketFocus,
          strategyUsed: s.strategyUsed,
          riskPerTradePct: s.riskPerTradePct,
          winRatePct: s.winRatePct,
          maxDrawdownPct: s.maxDrawdownPct,
          netReturnLabel: s.netReturnLabel,
          adherenceScore: s.adherenceScore,
          keyLesson: s.keyLesson,
        }));
        output = stories;
        outputSummary = `Loaded ${stories.length} anonymized internal & global success cohorts`;
        break;
      }

      case 'list_product_improvement_backlog': {
        const items = authoritativeDb.getProductFeedback();
        output = items.slice(0, 16).map((f) => ({
          id: f.id,
          title: f.title,
          category: f.category,
          status: f.status,
          upvotes: f.upvotes,
          testsVerified: Boolean(f.testsVerified),
        }));
        outputSummary = `Loaded ${items.length} persisted product improvement items`;
        break;
      }

      case 'record_product_improvement_task': {
        const title = String(params.input.title ?? '').trim();
        const summary = String(params.input.summary ?? '').trim();
        const category = (params.input.category as ProductFeedbackCategory) || 'Intelligence & AI';
        if (!title || !summary) {
          throw new Error('Title and summary are required to record a product improvement task.');
        }
        const created = authoritativeDb.addProductFeedback({
          id: `fb-agent-${Date.now()}`,
          title: title.slice(0, 120),
          category,
          description: summary.slice(0, 600),
          submittedBy: String(params.input.submittedBy ?? 'ProductDevelopmentAgent'),
          source: 'AI Chat Synthesis',
          upvotes: 1,
          status: 'Engineering Proposed',
          createdAt: 'Today',
          testsVerified: false,
        });
        output = created[0];
        outputSummary = `Persisted task "${title}" in Engineering Proposed (requires tests + human approval)`;
        break;
      }
    }

    const record: ToolExecutionRecord = {
      id: `tool-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: new Date().toISOString(),
      domain: params.domain,
      agentName: params.agentName,
      toolName: params.toolName,
      permitted: true,
      inputSummary: JSON.stringify(params.input).slice(0, 140),
      outputSummary,
      durationMs: Math.max(1, Date.now() - start),
    };
    authoritativeDb.recordToolExecution(record);

    return { ok: true, data: output as TOutput, record };
  }
}

export const toolRegistry = new ToolRegistry();
