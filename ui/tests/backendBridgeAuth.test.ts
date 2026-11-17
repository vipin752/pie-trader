import assert from 'node:assert/strict';
import { createServer, type Server } from 'node:http';
import test from 'node:test';

import { fetchSyncedMarketContracts, probeBackendServices } from '../server/connectors/backendBridge';
import type { BackendConnectionConfig } from '../src/types/pie';

function startMockGateway(onRequest: (authHeader: string | undefined, path: string) => void): Promise<{
  port: number;
  close: () => Promise<void>;
}> {
  return new Promise((resolve) => {
    const server: Server = createServer((req, res) => {
      onRequest(req.headers.authorization, req.url ?? '');
      res.setHeader('Content-Type', 'application/json');
      if (req.url?.startsWith('/actuator/health')) {
        res.statusCode = 200;
        res.end(JSON.stringify({ status: 'UP' }));
        return;
      }
      if (req.url?.startsWith('/api/trade-card')) {
        if (!req.headers.authorization) {
          res.statusCode = 401;
          res.end(JSON.stringify({ error: 'unauthorized' }));
          return;
        }
        res.statusCode = 200;
        res.end(JSON.stringify({ status: 'OK', symbol: 'NIFTY', spot: 24000, atm: 24000, option: {}, risk: {} }));
        return;
      }
      res.statusCode = 404;
      res.end('{}');
    });
    server.listen(0, () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      resolve({ port, close: () => new Promise((r) => server.close(() => r())) });
    });
  });
}

test('S29-1: fetchSyncedMarketContracts forwards the real gateway access token as Authorization: Bearer', async () => {
  const seenAuthHeaders: (string | undefined)[] = [];
  const { port, close } = await startMockGateway((authHeader, path) => {
    if (path.startsWith('/api/trade-card')) seenAuthHeaders.push(authHeader);
  });

  try {
    const config: BackendConnectionConfig = {
      javaGatewayUrl: `http://127.0.0.1:${port}`,
      javaRiskEngineUrl: 'http://127.0.0.1:1',
      pythonAnalyticsUrl: 'http://127.0.0.1:1',
      preferExternalData: true,
    };

    const synced = await fetchSyncedMarketContracts(config, 'NIFTY 50', 'the-real-access-token');
    assert.equal(synced.usedExternal, true);
    assert.equal(seenAuthHeaders.length, 1);
    assert.equal(seenAuthHeaders[0], 'Bearer the-real-access-token');
  } finally {
    await close();
  }
});

test('S29-2: fetchSyncedMarketContracts without a token sends no Authorization header and the gateway rejects it (fallback kicks in)', async () => {
  const seenAuthHeaders: (string | undefined)[] = [];
  const { port, close } = await startMockGateway((authHeader, path) => {
    if (path.startsWith('/api/trade-card')) seenAuthHeaders.push(authHeader);
  });

  try {
    const config: BackendConnectionConfig = {
      javaGatewayUrl: `http://127.0.0.1:${port}`,
      javaRiskEngineUrl: 'http://127.0.0.1:1',
      pythonAnalyticsUrl: 'http://127.0.0.1:1',
      preferExternalData: true,
    };

    const synced = await fetchSyncedMarketContracts(config, 'NIFTY 50', null);
    assert.equal(synced.usedExternal, false, 'gateway 401s with no token, so local fallback data is used');
    assert.equal(seenAuthHeaders.length, 1);
    assert.equal(seenAuthHeaders[0], undefined);
  } finally {
    await close();
  }
});

test('S29-3: probeBackendServices reports whether this user actually has a forwardable gateway token', async () => {
  const { port, close } = await startMockGateway(() => {});
  try {
    const config: BackendConnectionConfig = {
      javaGatewayUrl: `http://127.0.0.1:${port}`,
      javaRiskEngineUrl: 'http://127.0.0.1:1',
      pythonAnalyticsUrl: 'http://127.0.0.1:1',
      preferExternalData: true,
    };

    const withToken = await probeBackendServices(config, 'some-token');
    const without = await probeBackendServices(config, null);

    const gw = (results: typeof withToken) => results.find((r) => r.serviceId === 'java-gateway')!;
    assert.match(gw(withToken).authStatus, /forwarding this user's access token/);
    assert.match(gw(without).authStatus, /no gateway token for this user/);
  } finally {
    await close();
  }
});
