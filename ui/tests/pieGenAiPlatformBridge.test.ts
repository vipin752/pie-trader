import assert from 'node:assert/strict';
import { createServer, type Server } from 'node:http';
import test from 'node:test';

// The bridge module reads PIE_GENAI_PLATFORM_URL once at import time, so it
// must be set before the dynamic import below (top-level `import` is
// hoisted and would run before any synchronous assignment in this file).
process.env.PIE_GENAI_PLATFORM_URL = 'http://127.0.0.1:59984';
const { chatWithPlatform, platformHealth } = await import('../server/ai/pieGenAiPlatformBridge');

test('S27-BFF-1: chatWithPlatform fails honestly (502) when the real platform is unreachable', async () => {
  await assert.rejects(chatWithPlatform('user-unreachable', 'hello'), (err: unknown) => {
    assert.ok(err instanceof Error);
    assert.equal((err as Error & { statusCode?: number }).statusCode, 502);
    assert.match(err.message, /Could not reach PIE GenAI Platform/);
    return true;
  });
});

test('S27-BFF-2: platformHealth reports unreachable honestly rather than throwing', async () => {
  const health = await platformHealth();
  assert.equal(health.reachable, false);
  assert.match(health.detail, /Could not reach PIE GenAI Platform/);
});

test('S27-BFF-3: chatWithPlatform does a real round trip against a running platform and reuses one conversation per user', async () => {
  const createdConversationIds: string[] = [];
  const contentByRunId = new Map<string, string>();
  let messageCount = 0;

  const server: Server = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {};
      res.setHeader('Content-Type', 'application/json');

      if (req.method === 'POST' && req.url === '/api/v1/conversations') {
        const id = `conv_${createdConversationIds.length + 1}`;
        createdConversationIds.push(id);
        res.statusCode = 201;
        res.end(JSON.stringify({ id, created_at: new Date().toISOString(), message_count: 0 }));
        return;
      }

      const submitMatch = req.url?.match(/^\/api\/v1\/conversations\/([^/]+)\/messages$/);
      if (req.method === 'POST' && submitMatch) {
        messageCount += 1;
        const runId = `run_${messageCount}`;
        contentByRunId.set(runId, String(body.content ?? ''));
        res.statusCode = 202;
        res.end(
          JSON.stringify({
            conversation_id: submitMatch[1],
            message_id: `msg_${messageCount}`,
            run_id: runId,
            status: 'completed',
            events_url: `/api/v1/runs/${runId}/events`,
            created_at: new Date().toISOString(),
          })
        );
        return;
      }

      const runMatch = req.url?.match(/^\/api\/v1\/runs\/([^/]+)$/);
      if (req.method === 'GET' && runMatch) {
        res.statusCode = 200;
        res.end(
          JSON.stringify({
            id: runMatch[1],
            conversation_id: createdConversationIds[createdConversationIds.length - 1],
            status: 'completed',
            created_at: new Date().toISOString(),
            completed_at: new Date().toISOString(),
            result_text: `echo: ${contentByRunId.get(runMatch[1]) ?? ''}`,
            error: null,
          })
        );
        return;
      }

      res.statusCode = 404;
      res.end(JSON.stringify({ detail: 'not found' }));
    });
  });

  await new Promise<void>((resolve) => server.listen(59984, resolve));
  try {
    const first = await chatWithPlatform('user-real', 'hello');
    assert.equal(first.status, 'completed');
    assert.equal(first.error, null);
    assert.equal(first.reply, 'echo: hello');

    const second = await chatWithPlatform('user-real', 'second message');
    assert.equal(second.conversationId, first.conversationId);
    assert.equal(createdConversationIds.length, 1, 'conversation is reused across messages for the same user');

    const thirdForDifferentUser = await chatWithPlatform('user-real-2', 'from another user');
    assert.notEqual(thirdForDifferentUser.conversationId, first.conversationId);

    const health = await platformHealth();
    assert.equal(health.reachable, false, 'this mock server has no /api/v1/readiness route, so health stays honest about that');
  } finally {
    await new Promise<void>((resolve) => server.close(() => resolve()));
  }
});
