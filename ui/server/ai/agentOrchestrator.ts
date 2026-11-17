import { Type } from '@google/genai';
import {
  IndexSymbol,
  ProductFeedbackCategory,
  ToolExecutionRecord,
} from '../../src/types/pie';
import { authoritativeDb } from '../store/authoritativeDb';
import { llmGateway, SupportedLlmModel } from './llmGateway';
import { toolRegistry } from './toolRegistry';

export class TradingIntelligenceAgent {
  public readonly agentName = 'TradingIntelligenceAgent';

  public async generateResearchBrief(params: {
    userId: string;
    opportunity: Record<string, unknown>;
    marketContext: Record<string, unknown>;
    globalNews: Record<string, unknown>[];
    model?: SupportedLlmModel;
  }) {
    const idx = (params.marketContext?.symbol as IndexSymbol) || 'NIFTY 50';
    const [capTool, sizeTool] = await Promise.all([
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'get_authoritative_capital_summary',
        input: {},
      }),
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'calculate_validated_lot_size',
        input: { index: idx },
      }),
    ]);

    const toolsExecuted = [capTool.record, sizeTool.record];
    const llmRes = await llmGateway.execute({
      preferredModel: params.model ?? 'gemini-4-hydrogen',
      systemPolicy: [
        'You are the PIE TradingIntelligenceAgent for Indian index options.',
        'Summarize ONLY verified inputs and authoritative engine outputs below.',
        'Produce 3 concise sections (under 180 words): (1) Cross-Dimensional Alignment, (2) Primary Invalidation & Event Risk, (3) PositionSizingEngine & Scaling Discipline.',
      ].join('\n'),
      untrustedContext: {
        opportunity: params.opportunity,
        marketContext: params.marketContext,
        relevantNews: (params.globalNews ?? []).slice(0, 4),
        authoritativeCapitalSummary: capTool.data,
        authoritativeLotSizing: sizeTool.data,
      },
      prompt: `Generate quantitative risk-first research synthesis for ${idx}.`,
    });

    authoritativeDb.recordAgentTask({
      id: `task-${Date.now()}`,
      createdAt: new Date().toISOString(),
      domain: 'TRADING_INTELLIGENCE',
      agentName: this.agentName,
      goal: `Research brief for ${idx}`,
      status: llmRes.usedFallback ? 'Fallback Used' : 'Completed',
      modelUsed: llmRes.modelUsed,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      estimatedCostInr: llmRes.estimatedCostInr,
      toolsInvoked: toolsExecuted.map((t) => t.toolName),
    });

    return {
      brief: llmRes.text,
      generatedAt: new Date().toISOString(),
      model: llmRes.modelUsed,
      usedFallback: llmRes.usedFallback,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      toolsExecuted,
    };
  }

  public async handleExperienceChat(params: {
    userId: string;
    message: string;
    history?: { role: 'user' | 'model'; text: string }[];
    model?: SupportedLlmModel;
    activeIndex?: IndexSymbol;
    userDeskContext?: Record<string, unknown>;
  }) {
    const idx: IndexSymbol = params.activeIndex ?? 'NIFTY 50';

    const [capTool, sizeTool, marketTool, benchTool, backlogTool] = await Promise.all([
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'get_authoritative_capital_summary',
        input: {},
      }),
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'calculate_validated_lot_size',
        input: { index: idx },
      }),
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'get_market_and_option_snapshot',
        input: { index: idx },
      }),
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'get_collective_benchmarks',
        input: {},
      }),
      toolRegistry.executeTool({
        domain: 'TRADING_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'list_product_improvement_backlog',
        input: {},
      }),
    ]);

    const toolsExecuted: ToolExecutionRecord[] = [
      capTool.record,
      sizeTool.record,
      marketTool.record,
      benchTool.record,
      backlogTool.record,
    ];

    authoritativeDb.appendConversationTurn({
      id: `turn-u-${Date.now()}`,
      userId: params.userId,
      domain: 'TRADING_INTELLIGENCE',
      role: 'user',
      text: params.message.trim(),
      timestamp: new Date().toISOString(),
    });

    const llmRes = await llmGateway.execute({
      preferredModel: params.model ?? 'gemini-4-hydrogen',
      history: (params.history ?? []).slice(-8),
      systemPolicy: [
        'You are the PIE Trader Experience Coach (TradingIntelligenceAgent).',
        '1) Compare the user experience against Internal PIE Desk & Global Benchmark cohorts provided in UNTRUSTED_RETRIEVED_CONTEXT_JSON.',
        '2) Cite exact numbers from authoritativeCapitalSummary and authoritativeLotSizing (never invent capital limits or recommend averaging down on losers).',
        '3) If the user reports UX friction, missing safety features, or product ideas, set productImprovementCandidate.detected = true so ProductDevelopmentAgent can log it separately without modifying live RiskManager rules.',
      ].join('\n'),
      untrustedContext: {
        authoritativeCapitalSummary: capTool.data,
        authoritativeLotSizing: sizeTool.data,
        marketSnapshot: marketTool.data,
        collectiveSuccessBenchmarks: benchTool.data,
        existingProductBacklog: backlogTool.data,
        clientDeskPreferences: params.userDeskContext ?? {},
      },
      prompt: params.message.trim(),
      responseSchema: {
        type: Type.OBJECT,
        properties: {
          reply: { type: Type.STRING },
          referencedStoryIds: {
            type: Type.ARRAY,
            items: { type: Type.STRING },
          },
          suggestedActions: {
            type: Type.ARRAY,
            items: { type: Type.STRING },
          },
          productImprovementCandidate: {
            type: Type.OBJECT,
            properties: {
              detected: { type: Type.BOOLEAN },
              title: { type: Type.STRING },
              category: { type: Type.STRING },
              summary: { type: Type.STRING },
            },
            required: ['detected', 'title', 'category', 'summary'],
          },
        },
        required: ['reply', 'referencedStoryIds', 'suggestedActions', 'productImprovementCandidate'],
      },
    });

    const parsed = JSON.parse((llmRes.text || '{}').trim()) as {
      reply?: string;
      referencedStoryIds?: string[];
      suggestedActions?: string[];
      productImprovementCandidate?: {
        detected?: boolean;
        title?: string;
        category?: ProductFeedbackCategory;
        summary?: string;
      };
    };

    const replyText = parsed.reply ?? 'No response generated.';

    authoritativeDb.appendConversationTurn({
      id: `turn-m-${Date.now()}`,
      userId: params.userId,
      domain: 'TRADING_INTELLIGENCE',
      role: 'model',
      text: replyText,
      modelUsed: llmRes.modelUsed,
      toolsExecuted: toolsExecuted.map((t) => t.toolName),
      timestamp: new Date().toISOString(),
    });

    authoritativeDb.recordAgentTask({
      id: `task-${Date.now()}`,
      createdAt: new Date().toISOString(),
      domain: 'TRADING_INTELLIGENCE',
      agentName: this.agentName,
      goal: `Experience coaching (${idx})`,
      status: llmRes.usedFallback ? 'Fallback Used' : 'Completed',
      modelUsed: llmRes.modelUsed,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      estimatedCostInr: llmRes.estimatedCostInr,
      toolsInvoked: toolsExecuted.map((t) => t.toolName),
    });

    return {
      modelUsed: llmRes.modelUsed,
      usedFallback: llmRes.usedFallback,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      domain: 'TRADING_INTELLIGENCE' as const,
      toolsExecuted: toolsExecuted.map((t) => ({
        toolName: t.toolName,
        durationMs: t.durationMs,
        permitted: t.permitted,
      })),
      reply: replyText,
      referencedStoryIds: Array.isArray(parsed.referencedStoryIds) ? parsed.referencedStoryIds : [],
      suggestedActions: Array.isArray(parsed.suggestedActions) ? parsed.suggestedActions : [],
      productImprovementCandidate: parsed.productImprovementCandidate?.detected
        ? {
            title: parsed.productImprovementCandidate.title ?? 'Workflow Improvement',
            category: parsed.productImprovementCandidate.category ?? 'Intelligence & AI',
            summary: parsed.productImprovementCandidate.summary ?? '',
          }
        : null,
      generatedAt: new Date().toISOString(),
    };
  }
}

export class ProductDevelopmentAgent {
  public readonly agentName = 'ProductDevelopmentAgent';

  public async synthesizeProductPriorities(params: {
    userId: string;
    model?: SupportedLlmModel;
  }) {
    const [benchTool, backlogTool] = await Promise.all([
      toolRegistry.executeTool({
        domain: 'PRODUCT_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'get_collective_benchmarks',
        input: {},
      }),
      toolRegistry.executeTool({
        domain: 'PRODUCT_INTELLIGENCE',
        agentName: this.agentName,
        userId: params.userId,
        toolName: 'list_product_improvement_backlog',
        input: {},
      }),
    ]);

    // Explicitly demonstrate domain boundary enforcement in audit log (Product Agent never accesses capital mutation)
    const toolsExecuted = [benchTool.record, backlogTool.record];

    const llmRes = await llmGateway.execute({
      preferredModel: params.model ?? 'gemini-4-hydrogen',
      systemPolicy: [
        'You are the PIE ProductDevelopmentAgent operating strictly in the Product Intelligence domain.',
        'You may analyze anonymized trader success cohorts and persisted product feedback.',
        'You must NEVER propose bypassing RiskManager, broker auth, or live user capital limits.',
        'Output 3 crisp engineering sections (under 200 words): (1) Common DNA of Successful Traders, (2) Top 2 Product Improvements Requiring Test & Approval, (3) Capital-Safety Impact.',
      ].join('\n'),
      untrustedContext: {
        collectiveBenchmarks: benchTool.data,
        productFeedbackBacklog: backlogTool.data,
      },
      prompt: 'Synthesize product roadmap priorities with clear test & governance boundaries.',
    });

    authoritativeDb.recordAgentTask({
      id: `task-prod-${Date.now()}`,
      createdAt: new Date().toISOString(),
      domain: 'PRODUCT_INTELLIGENCE',
      agentName: this.agentName,
      goal: 'Synthesize future product roadmap priorities',
      status: llmRes.usedFallback ? 'Fallback Used' : 'Completed',
      modelUsed: llmRes.modelUsed,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      estimatedCostInr: llmRes.estimatedCostInr,
      toolsInvoked: toolsExecuted.map((t) => t.toolName),
    });

    return {
      synthesis: llmRes.text,
      modelUsed: llmRes.modelUsed,
      usedFallback: llmRes.usedFallback,
      latencyMs: llmRes.latencyMs,
      estimatedTokens: llmRes.estimatedTokens,
      domain: 'PRODUCT_INTELLIGENCE' as const,
      toolsExecuted: toolsExecuted.map((t) => ({
        toolName: t.toolName,
        durationMs: t.durationMs,
        permitted: t.permitted,
      })),
      generatedAt: new Date().toISOString(),
    };
  }
}

export const tradingIntelligenceAgent = new TradingIntelligenceAgent();
export const productDevelopmentAgent = new ProductDevelopmentAgent();
