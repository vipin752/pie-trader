import {
  AgentTaskRecord,
  AuthProviderId,
  AuthProviderInfo,
  BackendConnectionConfig,
  BackendServiceProbeResult,
  BrokerConnection,
  CapitalDeploymentConfig,
  LlmBenchmarkStats,
  ProductFeedbackCategory,
  ProductFeedbackItem,
  ProductFeedbackStatus,
  PsychologyLog,
  RiskLimits,
  RiskProfile,
  StrategyAllocation,
  SuccessStory,
  ToolExecutionRecord,
  TradeOpportunity,
  UserDeskState,
  UserSession,
} from '../types/pie';

const SESSION_TOKEN_KEY = 'pie_session_token_ui_v1';

export interface AiTelemetrySnapshot {
  storageEngine: string;
  multiUserTenantCount: number;
  conversationsCount: number;
  agentTasks: AgentTaskRecord[];
  toolExecutions: ToolExecutionRecord[];
  llmBenchmarks: LlmBenchmarkStats[];
}

export function getStoredSessionToken(): string {
  try {
    return sessionStorage.getItem(SESSION_TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

export function setStoredSessionToken(token: string): void {
  try {
    if (token) {
      sessionStorage.setItem(SESSION_TOKEN_KEY, token);
    } else {
      sessionStorage.removeItem(SESSION_TOKEN_KEY);
    }
  } catch {
    // Ignore storage restriction
  }
}

async function apiRequest<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = getStoredSessionToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers['x-pie-session'] = token;
    headers['Authorization'] = `Bearer ${token}`;
  }

  const resp = await fetch(url, { ...options, headers });
  const data = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    const err = new Error(data.error || `Request failed (${resp.status})`) as Error & {
      desk?: UserDeskState;
      status?: number;
    };
    err.desk = data.desk;
    err.status = resp.status;
    throw err;
  }
  return data as T;
}

export const pieApi = {
  getAuthProviders: () =>
    apiRequest<{ baseUrl: string; providers: AuthProviderInfo[] }>('/api/auth/providers'),

  register: async (payload: {
    email: string;
    password: string;
    displayName?: string;
    enableTotp?: boolean;
    riskProfile: RiskProfile;
    sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
  }) => {
    const res = await apiRequest<{
      session: UserSession;
      token: string;
      desk: UserDeskState;
      totpSetup?: { secret: string; currentCodePreview: string; otpauthUri: string };
    }>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    setStoredSessionToken(res.token);
    return res;
  },

  login: async (payload: {
    email: string;
    password: string;
    totp: string;
    riskProfile: RiskProfile;
    sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
  }) => {
    const res = await apiRequest<{ session: UserSession; token: string; desk: UserDeskState }>(
      '/api/auth/login',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
    setStoredSessionToken(res.token);
    return res;
  },

  getOAuthUrl: (
    provider: Exclude<AuthProviderId, 'email'>,
    riskProfile: RiskProfile,
    sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE'
  ) =>
    apiRequest<{ url: string; callbackUrl: string }>(
      `/api/auth/oauth/${encodeURIComponent(provider)}/url?riskProfile=${encodeURIComponent(
        riskProfile
      )}&sessionMode=${encodeURIComponent(sessionMode)}`
    ),

  logout: async () => {
    setStoredSessionToken('');
    await apiRequest('/api/auth/logout', { method: 'POST' }).catch(() => undefined);
  },

  getDeskState: () =>
    apiRequest<{
      desk: UserDeskState;
      stories: SuccessStory[];
      productFeedback: ProductFeedbackItem[];
      telemetry: AiTelemetrySnapshot;
    }>('/api/desk/state'),

  toggleKillSwitch: () =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/kill-switch',
      { method: 'POST' }
    ),

  adjustAuthorizedAllocation: (nextAmountInr: number) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/capital/allocation',
      { method: 'POST', body: JSON.stringify({ nextAmountInr }) }
    ),

  updateDeploymentConfig: (patch: Partial<CapitalDeploymentConfig>) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/capital/config', {
      method: 'POST',
      body: JSON.stringify({ patch }),
    }),

  updateStrategy: (id: string, patch: Partial<StrategyAllocation>) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/strategies/update', {
      method: 'POST',
      body: JSON.stringify({ id, patch }),
    }),

  runAutomatedCycle: () =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/automation/run-cycle',
      { method: 'POST' }
    ),

  confirmReservationFill: (reservationId: string, partialRatio = 1) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      `/api/desk/reservations/${encodeURIComponent(reservationId)}/fill`,
      { method: 'POST', body: JSON.stringify({ partialRatio }) }
    ),

  releaseReservation: (reservationId: string) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      `/api/desk/reservations/${encodeURIComponent(reservationId)}/release`,
      { method: 'POST' }
    ),

  executePaperOpportunity: (opportunity: TradeOpportunity) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/orders/paper',
      { method: 'POST', body: JSON.stringify({ opportunity }) }
    ),

  syncBasketLegs: (basketLegs: UserDeskState['basketLegs']) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/basket/sync', {
      method: 'POST',
      body: JSON.stringify({ basketLegs }),
    }),

  executeBasketPaper: (basketLegs: UserDeskState['basketLegs']) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/basket/execute',
      { method: 'POST', body: JSON.stringify({ basketLegs }) }
    ),

  scalePosition: (posId: string, addLots: number, confirmedBreakout: boolean) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      `/api/desk/positions/${encodeURIComponent(posId)}/scale`,
      { method: 'POST', body: JSON.stringify({ addLots, confirmedBreakout }) }
    ),

  adjustPositionLtp: (posId: string, delta: number) =>
    apiRequest<{ desk: UserDeskState }>(`/api/desk/positions/${encodeURIComponent(posId)}/ltp`, {
      method: 'POST',
      body: JSON.stringify({ delta }),
    }),

  exitPosition: (posId: string, exitLots: number) =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      `/api/desk/positions/${encodeURIComponent(posId)}/exit`,
      { method: 'POST', body: JSON.stringify({ exitLots }) }
    ),

  emergencySquareOffAll: () =>
    apiRequest<{ desk: UserDeskState; notice: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/positions/square-off-all',
      { method: 'POST' }
    ),

  updateLimits: (payload: { profile?: RiskProfile; patch?: Partial<RiskLimits> }) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/limits', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateMood: (mood: PsychologyLog['selfAssessmentMood']) =>
    apiRequest<{ desk: UserDeskState; notice?: { type: 'ok' | 'warn'; text: string } }>(
      '/api/desk/psychology/mood',
      { method: 'POST', body: JSON.stringify({ mood }) }
    ),

  toggleBroker: (payload: {
    id: BrokerConnection['id'];
    action: 'connection' | 'permission';
    perm?: 'marketDataPerm' | 'portfolioPerm' | 'orderPlacementPerm';
  }) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/brokers/toggle', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  toggleWatchlist: (id: string) =>
    apiRequest<{ desk: UserDeskState }>('/api/desk/watchlist/toggle', {
      method: 'POST',
      body: JSON.stringify({ id }),
    }),

  addSuccessStory: (story: Omit<SuccessStory, 'id' | 'createdAt' | 'verifiedByDesk'>) =>
    apiRequest<{ stories: SuccessStory[] }>('/api/stories', {
      method: 'POST',
      body: JSON.stringify(story),
    }),

  addProductFeedback: (item: {
    title: string;
    category: ProductFeedbackCategory;
    description: string;
    source: ProductFeedbackItem['source'];
    submittedBy?: string;
  }) =>
    apiRequest<{ productFeedback: ProductFeedbackItem[] }>('/api/product/feedback', {
      method: 'POST',
      body: JSON.stringify(item),
    }),

  upvoteProductFeedback: (id: string) =>
    apiRequest<{ productFeedback: ProductFeedbackItem[] }>(
      `/api/product/feedback/${encodeURIComponent(id)}/upvote`,
      { method: 'POST' }
    ),

  transitionProductFeedback: (
    id: string,
    payload: {
      nextStatus: ProductFeedbackStatus;
      testsVerified?: boolean;
      versionTag?: string;
      note?: string;
    }
  ) =>
    apiRequest<{ productFeedback: ProductFeedbackItem[] }>(
      `/api/product/feedback/${encodeURIComponent(id)}/transition`,
      { method: 'POST', body: JSON.stringify(payload) }
    ),

  getAiTelemetry: () => apiRequest<AiTelemetrySnapshot>('/api/ai/telemetry'),

  probeBackends: (config: BackendConnectionConfig) =>
    apiRequest<{ results: BackendServiceProbeResult[] }>('/api/backend/probe', {
      method: 'POST',
      body: JSON.stringify(config),
    }),

  syncBackendContracts: (config: BackendConnectionConfig, symbol: string) =>
    apiRequest<{
      marketContext: Record<string, unknown>;
      optionAnalytics: Record<string, unknown>;
      usedExternal: boolean;
      note: string;
    }>('/api/backend/sync', {
      method: 'POST',
      body: JSON.stringify({ ...config, symbol }),
    }),
};
