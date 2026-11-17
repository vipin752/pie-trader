import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createOAuthAuthorizationUrl,
  exchangeOAuthCallback,
  generateTotpCode,
  generateTotpSecret,
  getAuthProvidersStatus,
  hashPassword,
  signSessionToken,
  verifyPassword,
  verifySessionToken,
  verifyTotpCode,
} from '../server/auth/authService';
import { authoritativeDb } from '../server/store/authoritativeDb';

test('AUTH-1: scrypt password hashing verifies valid passwords and rejects invalid ones', () => {
  const hash = hashPassword('NiftyDiscipline#2026');
  assert.ok(hash.includes(':'));
  assert.equal(verifyPassword('NiftyDiscipline#2026', hash), true);
  assert.equal(verifyPassword('WrongPassphrase', hash), false);
  assert.equal(verifyPassword('NiftyDiscipline#2026', 'malformed'), false);
});

test('AUTH-2: RFC 6238 TOTP verifies valid 6-digit code and rejects wrong codes', () => {
  const secret = generateTotpSecret();
  assert.ok(secret.length >= 16);
  const now = 1_763_300_000_000;
  const code = generateTotpCode(secret, now);
  assert.match(code, /^\d{6}$/);
  assert.equal(verifyTotpCode(secret, code, now), true);
  assert.equal(verifyTotpCode(secret, '000000', now), code === '000000');
  assert.equal(verifyTotpCode(secret, '12ab34', now), false);
});

test('AUTH-3: Signed session tokens reject tampering and expiration', () => {
  const { token, session } = signSessionToken(
    {
      email: 'vipin@pietrader.in',
      displayName: 'Vipin',
      role: 'Quantitative Options Desk',
      riskProfile: 'Conservative',
      mfaVerified: true,
      sessionMode: 'PAPER_DESK',
      authProvider: 'email',
    },
    60_000
  );

  const verified = verifySessionToken(token);
  assert.equal(verified?.email, session.email);
  assert.equal(verified?.authProvider, 'email');

  const [payload, sig] = token.split('.');
  const tamperedPayload = Buffer.from(
    JSON.stringify({ ...session, email: 'attacker@evil.in' }),
    'utf-8'
  ).toString('base64url');
  assert.equal(verifySessionToken(`${tamperedPayload}.${sig}`), null);
  assert.equal(verifySessionToken(`${payload}.invalidsig`), null);

  const expired = signSessionToken(
    {
      email: 'expired@pietrader.in',
      displayName: 'Expired',
      role: 'Quantitative Options Desk',
      riskProfile: 'Conservative',
      mfaVerified: false,
      sessionMode: 'PAPER_DESK',
    },
    -5_000
  );
  assert.equal(verifySessionToken(expired.token), null);
});

test('AUTH-4: Provider discovery reflects env config and generates PKCE URLs only when configured', async () => {
  const prevId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const prevSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  const prevMsId = process.env.MICROSOFT_OAUTH_CLIENT_ID;
  const prevMsSecret = process.env.MICROSOFT_OAUTH_CLIENT_SECRET;

  delete process.env.GOOGLE_OAUTH_CLIENT_ID;
  delete process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  delete process.env.MICROSOFT_OAUTH_CLIENT_ID;
  delete process.env.MICROSOFT_OAUTH_CLIENT_SECRET;

  try {
    const unconfigured = getAuthProvidersStatus('https://example.run.app');
    const google = unconfigured.find((p) => p.id === 'google');
    const microsoft = unconfigured.find((p) => p.id === 'microsoft');
    const email = unconfigured.find((p) => p.id === 'email');

    assert.equal(email?.configured, true);
    assert.equal(google?.configured, false);
    assert.equal(microsoft?.configured, false);
    assert.equal(google?.callbackUrl, 'https://example.run.app/api/auth/oauth/google/callback');
    assert.equal(
      microsoft?.callbackUrl,
      'https://example.run.app/api/auth/oauth/microsoft/callback'
    );

    const blockedUrl = createOAuthAuthorizationUrl({
      provider: 'google',
      baseUrl: 'https://example.run.app',
      riskProfile: 'Conservative',
      sessionMode: 'PAPER_DESK',
    });
    assert.equal(blockedUrl.ok, false);

    process.env.GOOGLE_OAUTH_CLIENT_ID = 'test-google-client-id';
    process.env.GOOGLE_OAUTH_CLIENT_SECRET = 'test-google-client-secret';
    process.env.MICROSOFT_OAUTH_CLIENT_ID = 'test-ms-client-id';
    process.env.MICROSOFT_OAUTH_CLIENT_SECRET = 'test-ms-client-secret';

    const okGoogle = createOAuthAuthorizationUrl({
      provider: 'google',
      baseUrl: 'https://example.run.app',
      riskProfile: 'Moderate',
      sessionMode: 'PAPER_DESK',
    });
    assert.equal(okGoogle.ok, true);
    if (okGoogle.ok) {
      assert.ok(okGoogle.url.startsWith('https://accounts.google.com/o/oauth2/v2/auth?'));
      assert.ok(okGoogle.url.includes('code_challenge_method=S256'));
    }

    const okMs = createOAuthAuthorizationUrl({
      provider: 'microsoft',
      baseUrl: 'https://example.run.app',
      riskProfile: 'Conservative',
      sessionMode: 'READ_ONLY_LIVE',
    });
    assert.equal(okMs.ok, true);
    if (okMs.ok) {
      assert.ok(
        okMs.url.startsWith('https://login.microsoftonline.com/common/oauth2/v2.0/authorize?')
      );
    }

    const badCallback = await exchangeOAuthCallback({
      provider: 'google',
      code: 'dummy-code',
      state: 'non-existent-state',
    });
    assert.equal(badCallback.ok, false);
  } finally {
    if (prevId === undefined) delete process.env.GOOGLE_OAUTH_CLIENT_ID;
    else process.env.GOOGLE_OAUTH_CLIENT_ID = prevId;
    if (prevSecret === undefined) delete process.env.GOOGLE_OAUTH_CLIENT_SECRET;
    else process.env.GOOGLE_OAUTH_CLIENT_SECRET = prevSecret;
    if (prevMsId === undefined) delete process.env.MICROSOFT_OAUTH_CLIENT_ID;
    else process.env.MICROSOFT_OAUTH_CLIENT_ID = prevMsId;
    if (prevMsSecret === undefined) delete process.env.MICROSOFT_OAUTH_CLIENT_SECRET;
    else process.env.MICROSOFT_OAUTH_CLIENT_SECRET = prevMsSecret;
  }
});

test('AUTH-5: authoritativeDb links OAuth providers cleanly without overwriting password credentials', () => {
  const email = `oauth-test-${Date.now()}@outlook.com`;
  const linked = authoritativeDb.linkOAuthCredential({
    email,
    displayName: 'Outlook Trader',
    provider: 'microsoft',
  });
  assert.deepEqual(linked.linkedProviders, ['microsoft']);

  const linkedSecond = authoritativeDb.linkOAuthCredential({
    email,
    displayName: 'Outlook Trader',
    provider: 'google',
  });
  assert.deepEqual(linkedSecond.linkedProviders, ['microsoft', 'google']);
});
