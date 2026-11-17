import assert from 'node:assert/strict';
import { createServer, type Server } from 'node:http';
import test from 'node:test';

import {
  createOAuthAuthorizationUrl,
  exchangeOAuthCallback,
  getGatewayAccessToken,
} from '../server/auth/authService';

const ENV_KEYS = [
  'CUSTOM_OIDC_CLIENT_ID',
  'CUSTOM_OIDC_CLIENT_SECRET',
  'CUSTOM_OIDC_AUTH_URL',
  'CUSTOM_OIDC_TOKEN_URL',
  'CUSTOM_OIDC_USERINFO_URL',
] as const;

function withMockOidcServer(
  handleToken: (body: URLSearchParams) => { status: number; json: Record<string, unknown> },
  run: (port: number) => Promise<void>
): Promise<void> {
  return new Promise((resolve, reject) => {
    const server: Server = createServer((req, res) => {
      if (req.method === 'GET' && req.url === '/userinfo') {
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ email: 'trader@pietrader.in', name: 'PIE Trader', email_verified: true }));
        return;
      }
      if (req.method === 'POST' && req.url === '/token') {
        const chunks: Buffer[] = [];
        req.on('data', (c) => chunks.push(c));
        req.on('end', () => {
          const body = new URLSearchParams(Buffer.concat(chunks).toString('utf8'));
          const { status, json } = handleToken(body);
          res.statusCode = status;
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify(json));
        });
        return;
      }
      res.statusCode = 404;
      res.end('not found');
    });

    server.listen(0, async () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      try {
        await run(port);
        resolve();
      } catch (err) {
        reject(err);
      } finally {
        server.close();
      }
    });
  });
}

async function withKeycloakEnv(port: number, body: () => Promise<void>) {
  const previous: Record<string, string | undefined> = {};
  for (const key of ENV_KEYS) previous[key] = process.env[key];

  process.env.CUSTOM_OIDC_CLIENT_ID = 'pie-trader-web';
  process.env.CUSTOM_OIDC_CLIENT_SECRET = 'test-secret';
  process.env.CUSTOM_OIDC_AUTH_URL = `http://127.0.0.1:${port}/auth`;
  process.env.CUSTOM_OIDC_TOKEN_URL = `http://127.0.0.1:${port}/token`;
  process.env.CUSTOM_OIDC_USERINFO_URL = `http://127.0.0.1:${port}/userinfo`;

  try {
    await body();
  } finally {
    for (const key of ENV_KEYS) {
      if (previous[key] === undefined) delete process.env[key];
      else process.env[key] = previous[key];
    }
  }
}

test('S28-1: a real Keycloak(-shaped) access token is retained and returned by getGatewayAccessToken, not discarded after login', async () => {
  await withMockOidcServer(
    (body) => {
      assert.equal(body.get('grant_type'), 'authorization_code');
      return {
        status: 200,
        json: { access_token: 'real-keycloak-access-token', refresh_token: 'real-refresh-token', expires_in: 300 },
      };
    },
    async (port) => {
      await withKeycloakEnv(port, async () => {
        const authUrl = createOAuthAuthorizationUrl({
          provider: 'oidc',
          baseUrl: 'https://example.run.app',
          riskProfile: 'Conservative',
          sessionMode: 'PAPER_DESK',
        });
        assert.equal(authUrl.ok, true);
        if (!authUrl.ok) return;

        const state = new URL(authUrl.url).searchParams.get('state')!;
        const exchanged = await exchangeOAuthCallback({ provider: 'oidc', code: 'auth-code', state });
        assert.equal(exchanged.ok, true);
        if (!exchanged.ok) return;
        assert.equal(exchanged.email, 'trader@pietrader.in');

        const gatewayToken = await getGatewayAccessToken(exchanged.email);
        assert.equal(gatewayToken, 'real-keycloak-access-token');
      });
    }
  );
});

test('S28-2: a near-expired access token is refreshed transparently via the provider\'s refresh_token grant', async () => {
  let tokenCalls = 0;
  await withMockOidcServer(
    (body) => {
      tokenCalls += 1;
      if (body.get('grant_type') === 'authorization_code') {
        // Issued already-near-expiry, forcing the next getGatewayAccessToken
        // call to go through the refresh path for real.
        return { status: 200, json: { access_token: 'first-token', refresh_token: 'refresh-abc', expires_in: 1 } };
      }
      assert.equal(body.get('grant_type'), 'refresh_token');
      assert.equal(body.get('refresh_token'), 'refresh-abc');
      return { status: 200, json: { access_token: 'refreshed-token', refresh_token: 'refresh-abc', expires_in: 300 } };
    },
    async (port) => {
      await withKeycloakEnv(port, async () => {
        const authUrl = createOAuthAuthorizationUrl({
          provider: 'oidc',
          baseUrl: 'https://example.run.app',
          riskProfile: 'Conservative',
          sessionMode: 'PAPER_DESK',
        });
        assert.equal(authUrl.ok, true);
        if (!authUrl.ok) return;
        const state = new URL(authUrl.url).searchParams.get('state')!;

        const exchanged = await exchangeOAuthCallback({ provider: 'oidc', code: 'auth-code', state });
        assert.equal(exchanged.ok, true);
        if (!exchanged.ok) return;

        const refreshed = await getGatewayAccessToken(exchanged.email);
        assert.equal(refreshed, 'refreshed-token');
        assert.equal(tokenCalls, 2, 'one authorization_code call, one refresh_token call');
      });
    }
  );
});

test('S28-3: an expired token with no refresh_token returns null rather than a stale token', async () => {
  await withMockOidcServer(
    (body) => {
      assert.equal(body.get('grant_type'), 'authorization_code');
      // No refresh_token in the response at all.
      return { status: 200, json: { access_token: 'short-lived-token', expires_in: 1 } };
    },
    async (port) => {
      await withKeycloakEnv(port, async () => {
        const authUrl = createOAuthAuthorizationUrl({
          provider: 'oidc',
          baseUrl: 'https://example.run.app',
          riskProfile: 'Conservative',
          sessionMode: 'PAPER_DESK',
        });
        assert.equal(authUrl.ok, true);
        if (!authUrl.ok) return;
        const state = new URL(authUrl.url).searchParams.get('state')!;

        const exchanged = await exchangeOAuthCallback({ provider: 'oidc', code: 'auth-code', state });
        assert.equal(exchanged.ok, true);
        if (!exchanged.ok) return;

        const token = await getGatewayAccessToken(exchanged.email);
        assert.equal(token, null);
      });
    }
  );
});

test('S28-4: a user who never signed in via an OIDC provider has no gateway token at all', async () => {
  const token = await getGatewayAccessToken('never-logged-in-via-oidc@pietrader.in');
  assert.equal(token, null);
});
