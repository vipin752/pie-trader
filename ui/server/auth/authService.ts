import crypto from 'crypto';
import type { Request } from 'express';
import type { AuthProviderId, AuthProviderInfo, RiskProfile, UserSession } from '../../src/types/pie';

const EPHEMERAL_FALLBACK_SECRET = crypto.randomBytes(32).toString('hex');
const SESSION_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
const OAUTH_STATE_TTL_MS = 10 * 60 * 1000; // 10 minutes

function getSessionSecret(): string {
  const configured = process.env.PIE_SESSION_SECRET?.trim();
  if (configured && configured.length >= 16) {
    return configured;
  }
  return EPHEMERAL_FALLBACK_SECRET;
}

// ---------- Password Hashing (scrypt + timingSafeEqual) ----------
export function hashPassword(password: string): string {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${derived}`;
}

export function verifyPassword(password: string, storedHash: string): boolean {
  if (!storedHash || !storedHash.includes(':')) return false;
  const [salt, expectedHex] = storedHash.split(':');
  if (!salt || !expectedHex) return false;
  const derived = crypto.scryptSync(password, salt, 64);
  const expected = Buffer.from(expectedHex, 'hex');
  if (derived.length !== expected.length) return false;
  return crypto.timingSafeEqual(derived, expected);
}

// ---------- RFC 6238 TOTP (HMAC-SHA1) ----------
const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function generateTotpSecret(bytes = 20): string {
  const raw = crypto.randomBytes(bytes);
  let bits = 0;
  let value = 0;
  let out = '';
  for (const b of raw) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += BASE32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) {
    out += BASE32_ALPHABET[(value << (5 - bits)) & 31];
  }
  return out;
}

function decodeBase32(secret: string): Buffer {
  const clean = secret.toUpperCase().replace(/[^A-Z2-7]/g, '');
  let bits = 0;
  let value = 0;
  const bytes: number[] = [];
  for (const ch of clean) {
    const idx = BASE32_ALPHABET.indexOf(ch);
    if (idx < 0) continue;
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      bytes.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

export function generateTotpCode(secret: string, timestampMs = Date.now()): string {
  const key = decodeBase32(secret);
  const counter = Math.floor(timestampMs / 1000 / 30);
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter), 0);
  const hmac = crypto.createHmac('sha1', key).update(msg).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const code =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  return String(code % 1_000_000).padStart(6, '0');
}

export function verifyTotpCode(secret: string, inputCode: string, timestampMs = Date.now()): boolean {
  const normalized = inputCode.trim();
  if (!/^\d{6}$/.test(normalized)) return false;
  for (const driftSteps of [-1, 0, 1]) {
    const candidate = generateTotpCode(secret, timestampMs + driftSteps * 30_000);
    if (crypto.timingSafeEqual(Buffer.from(candidate), Buffer.from(normalized))) {
      return true;
    }
  }
  return false;
}

// ---------- Signed Session Tokens (HMAC-SHA256 + Expiration) ----------
export function signSessionToken(session: UserSession, ttlMs = SESSION_TTL_MS): {
  token: string;
  session: UserSession;
} {
  const now = Date.now();
  const enriched: UserSession = {
    ...session,
    expiresAt: session.expiresAt ?? now + ttlMs,
  };
  const payload = Buffer.from(JSON.stringify(enriched), 'utf-8').toString('base64url');
  const sig = crypto.createHmac('sha256', getSessionSecret()).update(payload).digest('base64url');
  return { token: `${payload}.${sig}`, session: enriched };
}

export function verifySessionToken(token: string | undefined): UserSession | null {
  if (!token || !token.includes('.')) return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  const [payload, sig] = parts;
  if (!payload || !sig) return null;

  const expected = crypto.createHmac('sha256', getSessionSecret()).update(payload).digest('base64url');
  const sigBuf = Buffer.from(sig);
  const expBuf = Buffer.from(expected);
  if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
    return null;
  }

  try {
    const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf-8')) as UserSession;
    if (!decoded?.email) return null;
    if (typeof decoded.expiresAt === 'number' && decoded.expiresAt <= Date.now()) {
      return null;
    }
    return decoded;
  } catch {
    return null;
  }
}

export function extractBearerOrSessionToken(req: Request): string | undefined {
  const sessionHeader = req.header('x-pie-session')?.trim();
  if (sessionHeader) return sessionHeader;
  const authHeader = req.header('authorization')?.trim();
  if (authHeader && /^Bearer\s+/i.test(authHeader)) {
    return authHeader.replace(/^Bearer\s+/i, '').trim();
  }
  return undefined;
}

// ---------- Base URL & Multi-Provider Client OAuth (Google/Gmail, Microsoft/Outlook, Custom OIDC) ----------
export function resolveAppBaseUrl(req?: Request): string {
  const configured = process.env.APP_URL?.trim();
  if (configured && configured !== 'MY_APP_URL' && /^https?:\/\//i.test(configured)) {
    return configured.replace(/\/+$/, '');
  }
  if (req) {
    const protoHeader = (req.header('x-forwarded-proto') || req.protocol || 'http').split(',')[0].trim();
    const hostHeader = (req.header('x-forwarded-host') || req.header('host') || 'localhost:3000')
      .split(',')[0]
      .trim();
    const isLocal = hostHeader.startsWith('localhost') || hostHeader.startsWith('127.0.0.1');
    const scheme = isLocal ? protoHeader : 'https';
    return `${scheme}://${hostHeader}`.replace(/\/+$/, '');
  }
  return 'http://localhost:3000';
}

interface InternalOAuthProviderConfig {
  id: Exclude<AuthProviderId, 'email'>;
  label: string;
  description: string;
  configured: boolean;
  clientId: string;
  clientSecret: string;
  authUrl: string;
  tokenUrl: string;
  userInfoUrl: string;
  scopes: string[];
  extraAuthParams?: Record<string, string>;
}

function getInternalOAuthConfigs(): Record<Exclude<AuthProviderId, 'email'>, InternalOAuthProviderConfig> {
  const googleClientId = process.env.GOOGLE_OAUTH_CLIENT_ID?.trim() || '';
  const googleClientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET?.trim() || '';

  const msTenant = process.env.MICROSOFT_OAUTH_TENANT?.trim() || 'common';
  const msClientId = process.env.MICROSOFT_OAUTH_CLIENT_ID?.trim() || '';
  const msClientSecret = process.env.MICROSOFT_OAUTH_CLIENT_SECRET?.trim() || '';

  const oidcName = process.env.CUSTOM_OIDC_NAME?.trim() || 'Enterprise OIDC SSO';
  const oidcClientId = process.env.CUSTOM_OIDC_CLIENT_ID?.trim() || '';
  const oidcClientSecret = process.env.CUSTOM_OIDC_CLIENT_SECRET?.trim() || '';
  const oidcAuthUrl = process.env.CUSTOM_OIDC_AUTH_URL?.trim() || '';
  const oidcTokenUrl = process.env.CUSTOM_OIDC_TOKEN_URL?.trim() || '';
  const oidcUserInfoUrl = process.env.CUSTOM_OIDC_USERINFO_URL?.trim() || '';
  const oidcScopes = (process.env.CUSTOM_OIDC_SCOPES?.trim() || 'openid email profile')
    .split(/[\s,]+/)
    .filter(Boolean);

  return {
    google: {
      id: 'google',
      label: 'Google / Gmail',
      description: 'OAuth 2.0 / OIDC login for Gmail & Google Workspace accounts.',
      configured: Boolean(googleClientId && googleClientSecret),
      clientId: googleClientId,
      clientSecret: googleClientSecret,
      authUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
      tokenUrl: 'https://oauth2.googleapis.com/token',
      userInfoUrl: 'https://openidconnect.googleapis.com/v1/userinfo',
      scopes: ['openid', 'email', 'profile'],
      extraAuthParams: { prompt: 'select_account', access_type: 'online' },
    },
    microsoft: {
      id: 'microsoft',
      label: 'Microsoft / Outlook',
      description: 'Entra ID v2 OAuth 2.0 for Outlook, Hotmail, Live & Microsoft 365.',
      configured: Boolean(msClientId && msClientSecret),
      clientId: msClientId,
      clientSecret: msClientSecret,
      authUrl: `https://login.microsoftonline.com/${encodeURIComponent(msTenant)}/oauth2/v2.0/authorize`,
      tokenUrl: `https://login.microsoftonline.com/${encodeURIComponent(msTenant)}/oauth2/v2.0/token`,
      userInfoUrl: 'https://graph.microsoft.com/oidc/userinfo',
      scopes: ['openid', 'email', 'profile', 'User.Read'],
      extraAuthParams: { prompt: 'select_account' },
    },
    oidc: {
      id: 'oidc',
      label: oidcName,
      description: 'Extensible OpenID Connect provider (Keycloak, Auth0, Okta, or GitHub OIDC).',
      configured: Boolean(oidcClientId && oidcClientSecret && oidcAuthUrl && oidcTokenUrl && oidcUserInfoUrl),
      clientId: oidcClientId,
      clientSecret: oidcClientSecret,
      authUrl: oidcAuthUrl,
      tokenUrl: oidcTokenUrl,
      userInfoUrl: oidcUserInfoUrl,
      scopes: oidcScopes,
    },
  };
}

export function getAuthProvidersStatus(baseUrl: string): AuthProviderInfo[] {
  const oauth = getInternalOAuthConfigs();
  return [
    {
      id: 'email',
      label: 'Desk Email + Passphrase',
      description: 'Verified scrypt passphrase + optional RFC 6238 TOTP 2FA.',
      configured: true,
    },
    ...(['google', 'microsoft', 'oidc'] as const).map((id) => ({
      id,
      label: oauth[id].label,
      description: oauth[id].description,
      configured: oauth[id].configured,
      callbackUrl: `${baseUrl}/api/auth/oauth/${id}/callback`,
    })),
  ];
}

interface PendingOAuthState {
  provider: Exclude<AuthProviderId, 'email'>;
  codeVerifier: string;
  redirectUri: string;
  riskProfile: RiskProfile;
  sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
  createdAt: number;
}

const pendingOAuthStates = new Map<string, PendingOAuthState>();

function pruneExpiredOAuthStates() {
  const now = Date.now();
  for (const [key, val] of pendingOAuthStates.entries()) {
    if (now - val.createdAt > OAUTH_STATE_TTL_MS) {
      pendingOAuthStates.delete(key);
    }
  }
}

export function createOAuthAuthorizationUrl(params: {
  provider: Exclude<AuthProviderId, 'email'>;
  baseUrl: string;
  riskProfile: RiskProfile;
  sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
}): { ok: true; url: string; callbackUrl: string } | { ok: false; status: number; error: string } {
  const cfg = getInternalOAuthConfigs()[params.provider];
  if (!cfg) {
    return { ok: false, status: 400, error: `Unsupported OAuth provider: ${params.provider}` };
  }
  if (!cfg.configured) {
    return {
      ok: false,
      status: 503,
      error: `${cfg.label} client OAuth is not configured on the server yet.`,
    };
  }

  pruneExpiredOAuthStates();
  const state = crypto.randomBytes(24).toString('base64url');
  const codeVerifier = crypto.randomBytes(32).toString('base64url');
  const codeChallenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');
  const redirectUri = `${params.baseUrl}/api/auth/oauth/${params.provider}/callback`;

  pendingOAuthStates.set(state, {
    provider: params.provider,
    codeVerifier,
    redirectUri,
    riskProfile: params.riskProfile,
    sessionMode: params.sessionMode,
    createdAt: Date.now(),
  });

  const search = new URLSearchParams({
    client_id: cfg.clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: cfg.scopes.join(' '),
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    ...(cfg.extraAuthParams || {}),
  });

  return {
    ok: true,
    url: `${cfg.authUrl}?${search.toString()}`,
    callbackUrl: redirectUri,
  };
}

// ---------- Gateway access token retention (Keycloak / any OIDC provider) ----------
//
// backend/pietrader-gateway is a real OAuth2 resource server now (Keycloak
// JWT, issuer/audience/permissions validated server-side there — see
// backend/pietrader-gateway's SecurityConfig). The OIDC token exchange
// above already receives a real access token from whichever provider is
// configured; previously it was used once to call the userinfo endpoint
// and then discarded, so nothing was ever forwarded to the gateway and
// every gateway call failed its own auth. This retains it, server-side
// only (never sent to the browser — the PIE session token above is a
// separate, unrelated credential), and refreshes it on demand.
interface GatewayTokenRecord {
  provider: Exclude<AuthProviderId, 'email'>;
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
}

const gatewayTokensByEmail = new Map<string, GatewayTokenRecord>();
const TOKEN_EXPIRY_SAFETY_MARGIN_MS = 30_000;

/**
 * The access token to forward as `Authorization: Bearer` when the BFF
 * calls backend/pietrader-gateway on this user's behalf. Refreshes via
 * the provider's own token endpoint if the stored one is near expiry.
 * Returns null if the user never signed in via an OIDC provider, or the
 * token can't be refreshed (gateway calls then proceed unauthenticated,
 * exactly as before this token-retention feature existed, and the
 * now-secured gateway will reject them — the BFF's own fallback to
 * local data already handles that path).
 */
export async function getGatewayAccessToken(email: string): Promise<string | null> {
  const record = gatewayTokensByEmail.get(email);
  if (!record) return null;

  if (record.expiresAt - TOKEN_EXPIRY_SAFETY_MARGIN_MS > Date.now()) {
    return record.accessToken;
  }

  if (!record.refreshToken) {
    gatewayTokensByEmail.delete(email);
    return null;
  }

  const cfg = getInternalOAuthConfigs()[record.provider];
  if (!cfg.configured) {
    gatewayTokensByEmail.delete(email);
    return null;
  }

  try {
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: cfg.clientId,
      client_secret: cfg.clientSecret,
      refresh_token: record.refreshToken,
    });
    const resp = await fetch(cfg.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: body.toString(),
    });
    if (!resp.ok) {
      gatewayTokensByEmail.delete(email);
      return null;
    }
    const json = (await resp.json()) as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
    };
    if (!json.access_token) {
      gatewayTokensByEmail.delete(email);
      return null;
    }
    const refreshed: GatewayTokenRecord = {
      provider: record.provider,
      accessToken: json.access_token,
      refreshToken: json.refresh_token ?? record.refreshToken,
      expiresAt: Date.now() + (typeof json.expires_in === 'number' ? json.expires_in * 1000 : 60_000),
    };
    gatewayTokensByEmail.set(email, refreshed);
    return refreshed.accessToken;
  } catch {
    // Network error reaching the provider — fail closed (no token),
    // never fall back to the stale/expired one.
    return null;
  }
}

export async function exchangeOAuthCallback(params: {
  provider: Exclude<AuthProviderId, 'email'>;
  code: string;
  state: string;
}): Promise<
  | {
      ok: true;
      email: string;
      displayName: string;
      emailVerified: boolean;
      riskProfile: RiskProfile;
      sessionMode: 'PAPER_DESK' | 'READ_ONLY_LIVE';
    }
  | { ok: false; status: number; error: string }
> {
  pruneExpiredOAuthStates();
  const pending = pendingOAuthStates.get(params.state);
  if (!pending || pending.provider !== params.provider) {
    return { ok: false, status: 400, error: 'Invalid or expired OAuth state parameter.' };
  }
  pendingOAuthStates.delete(params.state);

  const cfg = getInternalOAuthConfigs()[params.provider];
  if (!cfg || !cfg.configured) {
    return { ok: false, status: 503, error: 'OAuth provider is no longer configured.' };
  }

  const tokenBody = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: cfg.clientId,
    client_secret: cfg.clientSecret,
    code: params.code,
    redirect_uri: pending.redirectUri,
    code_verifier: pending.codeVerifier,
  });

  const tokenResp = await fetch(cfg.tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body: tokenBody.toString(),
  });

  if (!tokenResp.ok) {
    const errText = await tokenResp.text().catch(() => '');
    return {
      ok: false,
      status: 502,
      error: `Token exchange with ${cfg.label} failed (${tokenResp.status}): ${errText.slice(0, 180)}`,
    };
  }

  const tokenJson = (await tokenResp.json()) as {
    access_token?: string;
    refresh_token?: string;
    expires_in?: number;
  };
  if (!tokenJson.access_token) {
    return { ok: false, status: 502, error: `${cfg.label} did not return an access_token.` };
  }

  const profileResp = await fetch(cfg.userInfoUrl, {
    headers: { Authorization: `Bearer ${tokenJson.access_token}`, Accept: 'application/json' },
  });

  let profileData: Record<string, unknown> = {};
  if (profileResp.ok) {
    profileData = (await profileResp.json()) as Record<string, unknown>;
  } else if (params.provider === 'microsoft') {
    const graphMeResp = await fetch('https://graph.microsoft.com/v1.0/me', {
      headers: { Authorization: `Bearer ${tokenJson.access_token}`, Accept: 'application/json' },
    });
    if (!graphMeResp.ok) {
      return { ok: false, status: 502, error: 'Failed to retrieve Microsoft user profile.' };
    }
    profileData = (await graphMeResp.json()) as Record<string, unknown>;
  } else {
    return {
      ok: false,
      status: 502,
      error: `Failed to fetch user profile from ${cfg.label} (${profileResp.status}).`,
    };
  }

  const rawEmail = String(
    profileData.email || profileData.mail || profileData.userPrincipalName || ''
  )
    .trim()
    .toLowerCase();

  if (!rawEmail || !rawEmail.includes('@')) {
    return {
      ok: false,
      status: 400,
      error: `${cfg.label} profile did not include a verified email address.`,
    };
  }

  const emailVerified =
    profileData.email_verified === undefined ? true : Boolean(profileData.email_verified);
  if (!emailVerified) {
    return {
      ok: false,
      status: 403,
      error: `Email address from ${cfg.label} is not verified.`,
    };
  }

  const displayName = String(
    profileData.name || profileData.displayName || rawEmail.split('@')[0]
  ).slice(0, 80);

  // Retain the real access token server-side so the BFF can actually
  // authenticate to backend/pietrader-gateway on this user's behalf —
  // see getGatewayAccessToken() above. Keyed by the same normalized
  // email the PIE session itself uses.
  gatewayTokensByEmail.set(rawEmail, {
    provider: params.provider,
    accessToken: tokenJson.access_token,
    refreshToken: tokenJson.refresh_token,
    expiresAt: Date.now() + (typeof tokenJson.expires_in === 'number' ? tokenJson.expires_in * 1000 : 60_000),
  });

  return {
    ok: true,
    email: rawEmail,
    displayName,
    emailVerified: true,
    riskProfile: pending.riskProfile,
    sessionMode: pending.sessionMode,
  };
}
