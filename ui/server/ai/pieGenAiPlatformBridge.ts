/**
 * Bridge to the real, standalone PIE GenAI Platform service
 * (vipin752/pie-genai-platform — Python/FastAPI, self-hosted via Ollama by
 * default). This is the canonical backend for GenAI orchestration per
 * CR-005: this file only calls its existing REST contract, it never
 * reimplements orchestration, intent classification, or agent logic here.
 *
 * Unlike LlmGateway's LlmProviderAdapter (Gemini / OwnGenAi skeleton),
 * the real platform has its own server-side conversation/run lifecycle —
 * no client-supplied systemInstruction or responseSchema, no client-held
 * history (the platform's ConversationStore owns that). So this is a
 * separate, parallel integration surface rather than another
 * LlmProviderAdapter: see ui/server/ai/llmGateway.ts's own class
 * docs for why that contract doesn't fit here.
 *
 * Financial safety: the platform's own tools are read-only (trade-card,
 * dashboard, positions, risk — see pie-genai-platform's
 * docs/integration/PIE_TRADER_INTEGRATION.md). Nothing here ever calls a
 * pietrader-gateway write/execution endpoint, directly or indirectly.
 */

const PLATFORM_BASE_URL = (process.env.PIE_GENAI_PLATFORM_URL || 'http://localhost:8000').replace(/\/$/, '');

interface PlatformConversation {
  id: string;
  created_at: string;
  message_count: number;
}

interface PlatformSubmitMessageResponse {
  conversation_id: string;
  message_id: string;
  run_id: string;
  status: string;
  events_url: string;
  created_at: string;
}

interface PlatformRun {
  id: string;
  conversation_id: string;
  status: string;
  created_at: string;
  completed_at: string | null;
  result_text: string | null;
  error: string | null;
}

class PlatformUnreachableError extends Error {
  public readonly statusCode = 502;
  constructor(cause: unknown) {
    super(
      `Could not reach PIE GenAI Platform at ${PLATFORM_BASE_URL} — is the service running? ` +
        `(${cause instanceof Error ? cause.message : String(cause)})`
    );
  }
}

async function platformFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let resp: Response;
  try {
    resp = await fetch(`${PLATFORM_BASE_URL}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    });
  } catch (err) {
    throw new PlatformUnreachableError(err);
  }
  if (!resp.ok) {
    const body = await resp.text().catch(() => '');
    const err = new Error(`PIE GenAI Platform returned ${resp.status} for ${path}: ${body.slice(0, 200)}`);
    (err as Error & { statusCode?: number }).statusCode = resp.status >= 500 ? 502 : resp.status;
    throw err;
  }
  return (await resp.json()) as T;
}

/**
 * One conversation per BFF user session, created lazily and reused —
 * mirrors how a human would use the platform's own chat lifecycle rather
 * than creating a throwaway conversation per message. In-memory only
 * (matches authoritativeDb's own in-memory-for-now posture elsewhere in
 * this BFF); lost on restart, which just means a fresh conversation
 * starts next message.
 */
const conversationIdByUser = new Map<string, string>();

async function getOrCreateConversationId(userId: string): Promise<string> {
  const existing = conversationIdByUser.get(userId);
  if (existing) return existing;
  const conv = await platformFetch<PlatformConversation>('/api/v1/conversations', { method: 'POST' });
  conversationIdByUser.set(userId, conv.id);
  return conv.id;
}

export interface PlatformChatResult {
  reply: string;
  status: string;
  error: string | null;
  conversationId: string;
  runId: string;
}

/**
 * Submits a message to the real PIE GenAI Platform and returns its
 * answer (or its honest failure) — never a fabricated reply. The
 * platform runs synchronously today (see its orchestrator module
 * docstring), so by the time submit returns, the run is already
 * terminal; this still re-fetches the run so behavior keeps working
 * once the platform becomes genuinely async.
 */
export async function chatWithPlatform(userId: string, message: string): Promise<PlatformChatResult> {
  const conversationId = await getOrCreateConversationId(userId);

  let submitted: PlatformSubmitMessageResponse;
  try {
    submitted = await platformFetch<PlatformSubmitMessageResponse>(
      `/api/v1/conversations/${conversationId}/messages`,
      { method: 'POST', body: JSON.stringify({ content: message }) }
    );
  } catch (err) {
    // The conversation itself may have been lost (e.g. platform restarted
    // in-memory store) — drop it so the next message starts a fresh one
    // instead of repeating a 404 forever.
    if ((err as Error & { statusCode?: number }).statusCode === 404) {
      conversationIdByUser.delete(userId);
    }
    throw err;
  }

  const run = await platformFetch<PlatformRun>(`/api/v1/runs/${submitted.run_id}`);

  return {
    reply: run.result_text ?? '',
    status: run.status,
    error: run.error,
    conversationId,
    runId: run.id,
  };
}

export interface PlatformHealth {
  reachable: boolean;
  detail: string;
}

/** Never throws — used for an honest UI health indicator, not a gate. */
export async function platformHealth(): Promise<PlatformHealth> {
  try {
    const body = await platformFetch<{ status: string; checks: Record<string, string> }>('/api/v1/readiness');
    return { reachable: true, detail: `status=${body.status}` };
  } catch (err) {
    return { reachable: false, detail: err instanceof Error ? err.message : String(err) };
  }
}
