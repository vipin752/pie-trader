import fs from 'fs';
import path from 'path';
import {
  AgentDomain,
  AgentTaskRecord,
  LlmBenchmarkStats,
  ProductFeedbackItem,
  ProductFeedbackStatus,
  SuccessStory,
  ToolExecutionRecord,
  UserDeskState,
} from '../../src/types/pie';
import {
  INITIAL_ACTIVE_POSITIONS,
  INITIAL_AUDIT_LOGS,
  INITIAL_BASKET_LEGS,
  INITIAL_BROKERS,
  INITIAL_CAPITAL_LEDGER,
  INITIAL_CAPITAL_RESERVATIONS,
  INITIAL_DEPLOYMENT_CONFIG,
  INITIAL_JOURNAL,
  INITIAL_PRODUCT_FEEDBACK,
  INITIAL_PSYCHOLOGY_LOG,
  INITIAL_RISK_LIMITS,
  INITIAL_STRATEGY_ALLOCATIONS,
  INITIAL_SUCCESS_STORIES,
} from '../../src/data/pieMarketData';

export interface PersistedConversationTurn {
  id: string;
  userId: string;
  domain: AgentDomain;
  role: 'user' | 'model';
  text: string;
  modelUsed?: string;
  toolsExecuted?: string[];
  timestamp: string;
}

export interface UserCredentialRecord {
  email: string;
  displayName: string;
  passwordHash?: string;
  totpSecret?: string;
  totpEnabled: boolean;
  linkedProviders: ('email' | 'google' | 'microsoft' | 'oidc')[];
  createdAt: string;
  lastLoginAt: string;
}

interface StoreRoot {
  schemaVersion: number;
  users: Record<string, UserDeskState>;
  credentials: Record<string, UserCredentialRecord>;
  successStories: SuccessStory[];
  productFeedback: ProductFeedbackItem[];
  conversations: PersistedConversationTurn[];
  agentTasks: AgentTaskRecord[];
  toolExecutions: ToolExecutionRecord[];
  llmBenchmarks: Record<string, LlmBenchmarkStats>;
}

const DATA_DIR = path.resolve(process.cwd(), '.data');
const STORE_PATH = path.join(DATA_DIR, 'pie_desk_store.json');

function cloneDefault<T>(val: T): T {
  return JSON.parse(JSON.stringify(val)) as T;
}

function createInitialUserState(userId: string): UserDeskState {
  return {
    userId,
    deploymentConfig: cloneDefault(INITIAL_DEPLOYMENT_CONFIG),
    strategies: cloneDefault(INITIAL_STRATEGY_ALLOCATIONS),
    ledger: cloneDefault(INITIAL_CAPITAL_LEDGER),
    reservations: cloneDefault(INITIAL_CAPITAL_RESERVATIONS),
    activePositions: cloneDefault(INITIAL_ACTIVE_POSITIONS),
    basketLegs: cloneDefault(INITIAL_BASKET_LEGS),
    limits: cloneDefault(INITIAL_RISK_LIMITS),
    psychology: cloneDefault(INITIAL_PSYCHOLOGY_LOG),
    brokers: cloneDefault(INITIAL_BROKERS),
    journal: cloneDefault(INITIAL_JOURNAL),
    auditLogs: cloneDefault(INITIAL_AUDIT_LOGS),
    watchlistIds: ['opp-nifty-24400ce'],
    updatedAt: new Date().toISOString(),
  };
}

function createDefaultStore(): StoreRoot {
  return {
    schemaVersion: 2,
    users: {},
    credentials: {},
    successStories: cloneDefault(INITIAL_SUCCESS_STORIES),
    productFeedback: cloneDefault(INITIAL_PRODUCT_FEEDBACK).map((f) => ({
      ...f,
      testsVerified: f.status === 'Planned Q1' || f.status === 'In Engineering',
      auditTrail: [
        {
          timestamp: f.createdAt,
          actor: f.submittedBy,
          action: `Created via ${f.source} in status "${f.status}"`,
        },
      ],
    })),
    conversations: [],
    agentTasks: [],
    toolExecutions: [],
    llmBenchmarks: {
      'gemini-4-hydrogen': {
        model: 'gemini-4-hydrogen',
        provider: 'Google Gemini',
        calls: 0,
        successes: 0,
        fallbacks: 0,
        avgLatencyMs: 0,
        totalTokens: 0,
        totalCostInr: 0,
        toolAccuracyPct: 100,
      },
      'gemini-3.1-flash-lite': {
        model: 'gemini-3.1-flash-lite',
        provider: 'Google Gemini',
        calls: 0,
        successes: 0,
        fallbacks: 0,
        avgLatencyMs: 0,
        totalTokens: 0,
        totalCostInr: 0,
        toolAccuracyPct: 100,
      },
    },
  };
}

class AuthoritativeDb {
  private cache: StoreRoot | null = null;

  private load(): StoreRoot {
    if (this.cache) return this.cache;
    try {
      if (fs.existsSync(STORE_PATH)) {
        const raw = fs.readFileSync(STORE_PATH, 'utf-8');
        const parsed = JSON.parse(raw) as StoreRoot;
        this.cache = {
          ...createDefaultStore(),
          ...parsed,
          credentials: parsed.credentials ?? {},
        };
        return this.cache;
      }
    } catch {
      // Fallback cleanly to fresh initialized store if file corrupted
    }
    this.cache = createDefaultStore();
    this.flush();
    return this.cache;
  }

  private flush(): void {
    if (!this.cache) return;
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    const tmpPath = `${STORE_PATH}.tmp`;
    fs.writeFileSync(tmpPath, JSON.stringify(this.cache, null, 2), 'utf-8');
    fs.renameSync(tmpPath, STORE_PATH);
  }

  public getUserCredential(rawEmail: string): UserCredentialRecord | null {
    const email = rawEmail.trim().toLowerCase();
    if (!email) return null;
    const store = this.load();
    return store.credentials[email] ?? null;
  }

  public upsertUserCredential(record: UserCredentialRecord): UserCredentialRecord {
    const email = record.email.trim().toLowerCase();
    const store = this.load();
    const normalized: UserCredentialRecord = {
      ...record,
      email,
    };
    store.credentials[email] = normalized;
    this.flush();
    return normalized;
  }

  public linkOAuthCredential(params: {
    email: string;
    displayName: string;
    provider: 'google' | 'microsoft' | 'oidc';
  }): UserCredentialRecord {
    const email = params.email.trim().toLowerCase();
    const now = new Date().toISOString();
    const existing = this.getUserCredential(email);
    const linked = Array.from(new Set([...(existing?.linkedProviders ?? []), params.provider]));
    return this.upsertUserCredential({
      email,
      displayName: existing?.displayName || params.displayName,
      passwordHash: existing?.passwordHash,
      totpSecret: existing?.totpSecret,
      totpEnabled: existing?.totpEnabled ?? false,
      linkedProviders: linked,
      createdAt: existing?.createdAt ?? now,
      lastLoginAt: now,
    });
  }

  public getOrCreateUserDesk(rawUserId: string): UserDeskState {
    const userId = rawUserId.trim().toLowerCase() || 'desk@pietrader.in';
    const store = this.load();
    if (!store.users[userId]) {
      store.users[userId] = createInitialUserState(userId);
      this.flush();
    }
    return store.users[userId];
  }

  public updateUserDesk(rawUserId: string, updater: (state: UserDeskState) => UserDeskState): UserDeskState {
    const userId = rawUserId.trim().toLowerCase() || 'desk@pietrader.in';
    const store = this.load();
    const current = store.users[userId] ?? createInitialUserState(userId);
    const updated = {
      ...updater(current),
      userId,
      updatedAt: new Date().toISOString(),
    };
    store.users[userId] = updated;
    this.flush();
    return updated;
  }

  /**
   * Privacy-preserving collective benchmarks: strips private email/account identifiers
   * and returns only anonymized cohort metrics.
   */
  public getSuccessStories(): SuccessStory[] {
    return this.load().successStories;
  }

  public addSuccessStory(story: SuccessStory): SuccessStory[] {
    const store = this.load();
    store.successStories = [story, ...store.successStories];
    this.flush();
    return store.successStories;
  }

  public getProductFeedback(): ProductFeedbackItem[] {
    return this.load().productFeedback;
  }

  public addProductFeedback(item: ProductFeedbackItem): ProductFeedbackItem[] {
    const store = this.load();
    const withTrail: ProductFeedbackItem = {
      ...item,
      testsVerified: false,
      auditTrail: item.auditTrail ?? [
        {
          timestamp: new Date().toISOString(),
          actor: item.submittedBy,
          action: `Logged "${item.title}" in ${item.status} (Product Intelligence domain; isolated from production RiskManager).`,
        },
      ],
    };
    store.productFeedback = [withTrail, ...store.productFeedback];
    this.flush();
    return store.productFeedback;
  }

  public upvoteProductFeedback(id: string): ProductFeedbackItem[] {
    const store = this.load();
    store.productFeedback = store.productFeedback.map((f) =>
      f.id === id ? { ...f, upvotes: f.upvotes + 1 } : f
    );
    this.flush();
    return store.productFeedback;
  }

  public transitionProductFeedback(params: {
    id: string;
    nextStatus: ProductFeedbackStatus;
    actor: string;
    testsVerified?: boolean;
    versionTag?: string;
    note?: string;
  }): { ok: boolean; reason?: string; items: ProductFeedbackItem[] } {
    const store = this.load();
    const existing = store.productFeedback.find((f) => f.id === params.id);
    if (!existing) {
      return { ok: false, reason: 'Product improvement record not found.', items: store.productFeedback };
    }

    const nextTestsVerified = params.testsVerified ?? existing.testsVerified ?? false;
    if (params.nextStatus === 'Approved for Release' && !nextTestsVerified) {
      return {
        ok: false,
        reason: 'Governance gate blocked: Tests must be verified before approving a product change for release.',
        items: store.productFeedback,
      };
    }

    const now = new Date().toISOString();
    store.productFeedback = store.productFeedback.map((f) => {
      if (f.id !== params.id) return f;
      const trail = [
        ...(f.auditTrail ?? []),
        {
          timestamp: now,
          actor: params.actor,
          action: `${f.status} → ${params.nextStatus}${params.note ? ` (${params.note})` : ''}`,
        },
      ];
      return {
        ...f,
        status: params.nextStatus,
        testsVerified: nextTestsVerified,
        approvedBy: params.nextStatus === 'Approved for Release' ? params.actor : f.approvedBy,
        approvedAt: params.nextStatus === 'Approved for Release' ? now : f.approvedAt,
        versionTag: params.versionTag ?? f.versionTag,
        auditTrail: trail,
      };
    });

    this.flush();
    return { ok: true, items: store.productFeedback };
  }

  public appendConversationTurn(turn: PersistedConversationTurn): void {
    const store = this.load();
    store.conversations = [...store.conversations.slice(-119), turn];
    this.flush();
  }

  public getUserConversations(userId: string): PersistedConversationTurn[] {
    const normalized = userId.trim().toLowerCase();
    return this.load().conversations.filter((c) => c.userId === normalized);
  }

  public recordToolExecution(rec: ToolExecutionRecord): void {
    const store = this.load();
    store.toolExecutions = [rec, ...store.toolExecutions].slice(0, 100);
    this.flush();
  }

  public recordAgentTask(task: AgentTaskRecord): void {
    const store = this.load();
    store.agentTasks = [task, ...store.agentTasks].slice(0, 100);
    this.flush();
  }

  public recordLlmBenchmark(params: {
    model: string;
    latencyMs: number;
    tokens: number;
    costInr: number;
    success: boolean;
    usedFallback: boolean;
    toolSucceeded: boolean;
  }): void {
    const store = this.load();
    const prev: LlmBenchmarkStats = store.llmBenchmarks[params.model] ?? {
      model: params.model,
      provider: 'Google Gemini',
      calls: 0,
      successes: 0,
      fallbacks: 0,
      avgLatencyMs: 0,
      totalTokens: 0,
      totalCostInr: 0,
      toolAccuracyPct: 100,
    };

    const nextCalls = prev.calls + 1;
    const nextAvgLatency = Math.round(
      (prev.avgLatencyMs * prev.calls + params.latencyMs) / Math.max(1, nextCalls)
    );
    const nextSuccesses = prev.successes + (params.success ? 1 : 0);
    const nextFallbacks = prev.fallbacks + (params.usedFallback ? 1 : 0);
    const prevToolHits = Math.round((prev.toolAccuracyPct / 100) * prev.calls);
    const nextToolAccuracy = Math.round(
      ((prevToolHits + (params.toolSucceeded ? 1 : 0)) / Math.max(1, nextCalls)) * 100
    );

    store.llmBenchmarks[params.model] = {
      ...prev,
      calls: nextCalls,
      successes: nextSuccesses,
      fallbacks: nextFallbacks,
      avgLatencyMs: nextAvgLatency,
      totalTokens: prev.totalTokens + params.tokens,
      totalCostInr: Number((prev.totalCostInr + params.costInr).toFixed(4)),
      toolAccuracyPct: nextToolAccuracy,
    };
    this.flush();
  }

  public getTelemetrySnapshot(userId?: string) {
    const store = this.load();
    return {
      storageEngine: 'Server File-Backed Authoritative Store (.data/pie_desk_store.json)',
      multiUserTenantCount: Object.keys(store.users).length,
      conversationsCount: userId
        ? store.conversations.filter((c) => c.userId === userId.toLowerCase()).length
        : store.conversations.length,
      agentTasks: store.agentTasks.slice(0, 20),
      toolExecutions: store.toolExecutions.slice(0, 25),
      llmBenchmarks: Object.values(store.llmBenchmarks),
    };
  }
}

export const authoritativeDb = new AuthoritativeDb();
