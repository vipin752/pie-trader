import express, { NextFunction, Request, Response } from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import {
  ActivePosition,
  AuthProviderId,
  BackendConnectionConfig,
  BasketLeg,
  BrokerConnection,
  IndexSymbol,
  ProductFeedbackCategory,
  ProductFeedbackStatus,
  PsychologyLog,
  RiskProfile,
  TradeOpportunity,
  UserSession,
} from './src/types/pie';
import { INDICES_MASTER, INITIAL_OPPORTUNITIES } from './src/data/pieMarketData';
import {
  RISK_PROFILE_PRESETS,
  estimateOptionRoundTripCosts,
  validateBasketWithRiskManager,
} from './src/engine/pieEngine';
import {
  calculateStrategyLotSize,
  checkLiveAutomationPrerequisites,
  getCapitalDeploymentSummary,
  reconcilePartialOrFullFill,
  releaseOrRejectReservation,
  reserveCapitalAtomically,
  selectBestAutomatedOpportunity,
  validateAuthorizedAllocationChange,
} from './src/engine/capitalAllocationEngine';
import { authoritativeDb } from './server/store/authoritativeDb';
import {
  createOAuthAuthorizationUrl,
  exchangeOAuthCallback,
  extractBearerOrSessionToken,
  generateTotpCode,
  generateTotpSecret,
  getAuthProvidersStatus,
  hashPassword,
  resolveAppBaseUrl,
  signSessionToken,
  verifyPassword,
  verifySessionToken,
  verifyTotpCode,
} from './server/auth/authService';
import {
  productDevelopmentAgent,
  tradingIntelligenceAgent,
} from './server/ai/agentOrchestrator';
import {
  fetchSyncedMarketContracts,
  probeBackendServices,
} from './server/connectors/backendBridge';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = Number(process.env.PORT) || 3000;

app.use(express.json({ limit: '1mb' }));

interface AuthenticatedRequest extends Request {
  authSession?: UserSession;
}

function nowIst(): string {
  const d = new Date();
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')} IST`;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function requireAuthMiddleware(req: Request, res: Response, next: NextFunction): void {
  const token = extractBearerOrSessionToken(req);
  const session = verifySessionToken(token);
  if (!session?.email) {
    res.status(401).json({
      error: 'Authentication required. Sign in with Desk Email or configured Client OAuth.',
    });
    return;
  }
  (req as AuthenticatedRequest).authSession = session;
  next();
}

function getAuthenticatedUserId(req: Request): { userId: string; session: UserSession } {
  const session = (req as AuthenticatedRequest).authSession;
  if (!session?.email) {
    throw new Error('Unauthorized request reached handler without verified session.');
  }
  return { userId: session.email.trim().toLowerCase(), session };
}

function resolveDisplayName(email: string, explicitName?: string): string {
  if (explicitName && explicitName.trim()) {
    return explicitName.trim().slice(0, 80);
  }
  const namePart = email.split('@')[0].replace(/[._]/g, ' ');
  return (
    namePart
      .split(' ')
      .filter(Boolean)
      .map((s) => s[0].toUpperCase() + s.slice(1))
      .join(' ') || 'Desk Trader'
  );
}

// ---------- Health ----------
app.get('/api/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'pie-trader-desk',
    architecture: 'Express Authoritative Desk + Strict HMAC/OAuth2 + Modular Agentic AI Gateway',
    timestamp: new Date().toISOString(),
  });
});

// ---------- Authentication (Strict Email+Scrypt+TOTP + Multi-Provider Client OAuth) ----------
app.get('/api/auth/providers', (req: Request, res: Response) => {
  const baseUrl = resolveAppBaseUrl(req);
  res.json({
    baseUrl,
    providers: getAuthProvidersStatus(baseUrl),
  });
});

app.post('/api/auth/register', (req: Request, res: Response) => {
  const {
    email,
    password,
    displayName,
    enableTotp = false,
    riskProfile = 'Conservative',
    sessionMode = 'PAPER_DESK',
  } = req.body ?? {};

  if (!email || typeof email !== 'string' || !email.includes('@')) {
    res.status(400).json({ error: 'Enter a valid desk email address.' });
    return;
  }
  if (!password || typeof password !== 'string' || password.length < 6) {
    res.status(400).json({ error: 'Passphrase must be at least 6 characters.' });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  const existing = authoritativeDb.getUserCredential(normalizedEmail);
  if (existing?.passwordHash) {
    res.status(409).json({
      error: 'An account already exists for this email. Use Sign In instead.',
    });
    return;
  }

  const now = new Date().toISOString();
  const resolvedName = resolveDisplayName(normalizedEmail, displayName || existing?.displayName);
  const totpSecret = enableTotp ? generateTotpSecret() : undefined;

  authoritativeDb.upsertUserCredential({
    email: normalizedEmail,
    displayName: resolvedName,
    passwordHash: hashPassword(password),
    totpSecret,
    totpEnabled: Boolean(totpSecret),
    linkedProviders: Array.from(new Set([...(existing?.linkedProviders ?? []), 'email'])),
    createdAt: existing?.createdAt ?? now,
    lastLoginAt: now,
  });

  const validProfile: RiskProfile =
    riskProfile === 'Moderate' || riskProfile === 'Aggressive' ? riskProfile : 'Conservative';
  const validMode = sessionMode === 'READ_ONLY_LIVE' ? 'READ_ONLY_LIVE' : 'PAPER_DESK';

  const baseSession: UserSession = {
    email: normalizedEmail,
    displayName: resolvedName,
    role: 'Quantitative Options Desk',
    riskProfile: validProfile,
    mfaVerified: Boolean(totpSecret),
    sessionMode: validMode,
    authProvider: 'email',
  };

  const desk = authoritativeDb.updateUserDesk(normalizedEmail, (cur) => ({
    ...cur,
    limits: { ...cur.limits, ...RISK_PROFILE_PRESETS[validProfile] },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'USER',
        action: 'ACCOUNT_REGISTERED',
        detail: `${normalizedEmail} registered desk credentials (${validMode}, ${validProfile}).`,
        outcome: 'INFO',
      },
      ...cur.auditLogs,
    ],
  }));

  const { token, session } = signSessionToken(baseSession);
  res.status(201).json({
    session,
    token,
    desk,
    totpSetup: totpSecret
      ? {
          secret: totpSecret,
          currentCodePreview: generateTotpCode(totpSecret),
          otpauthUri: `otpauth://totp/PIE%20Trader:${encodeURIComponent(normalizedEmail)}?secret=${totpSecret}&issuer=PIE%20Trader`,
        }
      : undefined,
  });
});

app.post('/api/auth/login', (req: Request, res: Response) => {
  const {
    email,
    password,
    totp = '',
    riskProfile = 'Conservative',
    sessionMode = 'PAPER_DESK',
  } = req.body ?? {};

  if (!email || typeof email !== 'string' || !email.includes('@')) {
    res.status(400).json({ error: 'Enter a valid desk email address.' });
    return;
  }
  if (!password || typeof password !== 'string') {
    res.status(400).json({ error: 'Passphrase is required.' });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  const cred = authoritativeDb.getUserCredential(normalizedEmail);

  if (!cred) {
    res.status(401).json({
      error: 'No account found for this email. Switch to Create Account or sign in via configured OAuth.',
    });
    return;
  }
  if (!cred.passwordHash) {
    res.status(401).json({
      error: `This account uses OAuth (${cred.linkedProviders.join(', ')}). Sign in with your OAuth provider.`,
    });
    return;
  }
  if (!verifyPassword(password, cred.passwordHash)) {
    res.status(401).json({ error: 'Invalid desk email or passphrase.' });
    return;
  }
  if (cred.totpEnabled && cred.totpSecret && !verifyTotpCode(cred.totpSecret, String(totp))) {
    res.status(401).json({ error: 'Invalid or missing 6-digit 2FA TOTP code.' });
    return;
  }

  authoritativeDb.upsertUserCredential({
    ...cred,
    lastLoginAt: new Date().toISOString(),
  });

  const validProfile: RiskProfile =
    riskProfile === 'Moderate' || riskProfile === 'Aggressive' ? riskProfile : 'Conservative';
  const validMode = sessionMode === 'READ_ONLY_LIVE' ? 'READ_ONLY_LIVE' : 'PAPER_DESK';

  const baseSession: UserSession = {
    email: normalizedEmail,
    displayName: cred.displayName || resolveDisplayName(normalizedEmail),
    role: 'Quantitative Options Desk',
    riskProfile: validProfile,
    mfaVerified: Boolean(cred.totpEnabled),
    sessionMode: validMode,
    authProvider: 'email',
  };

  const desk = authoritativeDb.updateUserDesk(normalizedEmail, (cur) => ({
    ...cur,
    limits: { ...cur.limits, ...RISK_PROFILE_PRESETS[validProfile] },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'USER',
        action: 'SESSION_AUTHENTICATED',
        detail: `${normalizedEmail} signed in via verified Email/Scrypt (${validMode}, ${validProfile}).`,
        outcome: 'INFO',
      },
      ...cur.auditLogs,
    ],
  }));

  const { token, session } = signSessionToken(baseSession);
  res.json({ session, token, desk });
});

app.get('/api/auth/oauth/:provider/url', (req: Request, res: Response) => {
  const provider = req.params.provider as Exclude<AuthProviderId, 'email'>;
  if (provider !== 'google' && provider !== 'microsoft' && provider !== 'oidc') {
    res.status(400).json({ error: `Unsupported OAuth provider "${req.params.provider}".` });
    return;
  }

  const rawProfile = String(req.query.riskProfile || 'Conservative');
  const riskProfile: RiskProfile =
    rawProfile === 'Moderate' || rawProfile === 'Aggressive' ? rawProfile : 'Conservative';
  const sessionMode =
    String(req.query.sessionMode || 'PAPER_DESK') === 'READ_ONLY_LIVE'
      ? 'READ_ONLY_LIVE'
      : 'PAPER_DESK';

  const result = createOAuthAuthorizationUrl({
    provider,
    baseUrl: resolveAppBaseUrl(req),
    riskProfile,
    sessionMode,
  });

  if (result.ok === false) {
    res.status(result.status).json({ error: result.error });
    return;
  }
  res.json({ url: result.url, callbackUrl: result.callbackUrl });
});

app.get(
  ['/api/auth/oauth/:provider/callback', '/api/auth/oauth/:provider/callback/'],
  async (req: Request, res: Response) => {
    const provider = req.params.provider as Exclude<AuthProviderId, 'email'>;
    if (provider !== 'google' && provider !== 'microsoft' && provider !== 'oidc') {
      res.status(400).send('Unsupported OAuth provider.');
      return;
    }

    const oauthError = typeof req.query.error === 'string' ? req.query.error : '';
    const oauthErrorDesc =
      typeof req.query.error_description === 'string' ? req.query.error_description : '';
    if (oauthError) {
      const safeErr = escapeHtml(oauthErrorDesc || oauthError);
      res.status(400).send(`
        <!doctype html><html><body style="font-family:sans-serif;padding:24px;background:#0b1120;color:#f8fafc;">
          <h3>OAuth Authentication Cancelled</h3>
          <p>${safeErr}</p>
          <script>
            if (window.opener) {
              window.opener.postMessage({ type: 'PIE_OAUTH_ERROR', error: ${JSON.stringify(oauthErrorDesc || oauthError)} }, '*');
              window.close();
            }
          </script>
        </body></html>
      `);
      return;
    }

    const code = typeof req.query.code === 'string' ? req.query.code : '';
    const state = typeof req.query.state === 'string' ? req.query.state : '';
    if (!code || !state) {
      res.status(400).send('Missing OAuth code or state parameter.');
      return;
    }

    const exchanged = await exchangeOAuthCallback({ provider, code, state });
    if (exchanged.ok === false) {
      const safeMsg = escapeHtml(exchanged.error);
      res.status(exchanged.status).send(`
        <!doctype html><html><body style="font-family:sans-serif;padding:24px;background:#0b1120;color:#f8fafc;">
          <h3>OAuth Verification Failed</h3>
          <p>${safeMsg}</p>
          <script>
            if (window.opener) {
              window.opener.postMessage({ type: 'PIE_OAUTH_ERROR', error: ${JSON.stringify(exchanged.error)} }, '*');
              window.close();
            }
          </script>
        </body></html>
      `);
      return;
    }

    const cred = authoritativeDb.linkOAuthCredential({
      email: exchanged.email,
      displayName: exchanged.displayName,
      provider,
    });

    const baseSession: UserSession = {
      email: exchanged.email,
      displayName: cred.displayName,
      role: 'Quantitative Options Desk',
      riskProfile: exchanged.riskProfile,
      mfaVerified: true,
      sessionMode: exchanged.sessionMode,
      authProvider: provider,
    };

    authoritativeDb.updateUserDesk(exchanged.email, (cur) => ({
      ...cur,
      limits: { ...cur.limits, ...RISK_PROFILE_PRESETS[exchanged.riskProfile] },
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'USER',
          action: 'SESSION_OAUTH_AUTHENTICATED',
          detail: `${exchanged.email} signed in via ${provider.toUpperCase()} OAuth (${exchanged.sessionMode}).`,
          outcome: 'INFO',
        },
        ...cur.auditLogs,
      ],
    }));

    const { token, session } = signSessionToken(baseSession);
    res.send(`
      <!doctype html><html><body style="font-family:sans-serif;padding:24px;background:#0b1120;color:#f8fafc;">
        <h3>Authentication Successful</h3>
        <p>Returning to PIE Trader workspace...</p>
        <script>
          if (window.opener) {
            window.opener.postMessage({
              type: 'PIE_OAUTH_SUCCESS',
              token: ${JSON.stringify(token)},
              session: ${JSON.stringify(session)}
            }, '*');
            window.close();
          } else {
            window.location.href = '/';
          }
        </script>
      </body></html>
    `);
  }
);

app.get('/api/auth/session', (req: Request, res: Response) => {
  const token = extractBearerOrSessionToken(req);
  const session = verifySessionToken(token);
  if (!session) {
    res.status(401).json({ error: 'No valid or active session token.' });
    return;
  }
  res.json({ session });
});

app.post('/api/auth/logout', (_req, res) => {
  res.json({ ok: true });
});

// Require verified session token for ALL financial, desk, AI, and backend bridge routes
app.use(
  [
    '/api/desk',
    '/api/stories',
    '/api/product',
    '/api/research-brief',
    '/api/experience-chat',
    '/api/experience-insights',
    '/api/ai',
    '/api/backend',
  ],
  requireAuthMiddleware
);

// ---------- Authoritative Financial & Desk State ----------
app.get('/api/desk/state', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const desk = authoritativeDb.getOrCreateUserDesk(userId);
  res.json({
    desk,
    stories: authoritativeDb.getSuccessStories(),
    productFeedback: authoritativeDb.getProductFeedback(),
    telemetry: authoritativeDb.getTelemetrySnapshot(userId),
  });
});

app.post('/api/desk/kill-switch', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  let engaged = false;
  const desk = authoritativeDb.updateUserDesk(userId, (cur) => {
    engaged = !cur.limits.killSwitchEngaged;
    return {
      ...cur,
      limits: { ...cur.limits, killSwitchEngaged: engaged },
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: engaged ? 'KILL_SWITCH_ENGAGED' : 'KILL_SWITCH_RELEASED',
          detail: engaged
            ? 'Emergency Kill Switch blocked all order routing.'
            : 'Kill Switch disengaged by user.',
          outcome: engaged ? 'REJECTED' : 'INFO',
        },
        ...cur.auditLogs,
      ],
    };
  });
  res.json({
    desk,
    notice: {
      type: engaged ? 'warn' : 'ok',
      text: engaged
        ? 'Emergency Kill Switch engaged. All executions blocked.'
        : 'Kill Switch disengaged.',
    },
  });
});

app.post('/api/desk/capital/allocation', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { nextAmountInr } = req.body ?? {};
  const amount = Number(nextAmountInr);
  if (!Number.isFinite(amount)) {
    res.status(400).json({ error: 'Invalid authorized allocation amount.' });
    return;
  }

  const current = authoritativeDb.getOrCreateUserDesk(userId);
  const check = validateAuthorizedAllocationChange({
    config: current.deploymentConfig,
    nextAuthorizedInr: amount,
    strategies: current.strategies,
    reservations: current.reservations,
    activePositions: current.activePositions,
    ledger: current.ledger,
  });

  if (!check.ok) {
    const desk = authoritativeDb.updateUserDesk(userId, (cur) => ({
      ...cur,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'ALLOCATION_CHANGE_REJECTED',
          detail: check.reason ?? 'Invalid allocation',
          outcome: 'REJECTED',
        },
        ...cur.auditLogs,
      ],
    }));
    res.status(409).json({ error: check.reason, desk });
    return;
  }

  const desk = authoritativeDb.updateUserDesk(userId, (cur) => ({
    ...cur,
    deploymentConfig: { ...cur.deploymentConfig, authorizedAllocationInr: amount },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'USER',
        action: 'AUTHORIZED_ALLOCATION_UPDATED',
        detail: `Updated PIE Trader authorized allocation to ₹${amount.toLocaleString('en-IN')}.`,
        outcome: 'APPROVED',
      },
      ...cur.auditLogs,
    ],
  }));

  res.json({
    desk,
    notice: {
      type: 'ok',
      text: `Authorized PIE allocation persisted at ₹${amount.toLocaleString('en-IN')}.`,
    },
  });
});

app.post('/api/desk/capital/config', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const patch = req.body?.patch ?? {};
  const desk = authoritativeDb.updateUserDesk(userId, (cur) => ({
    ...cur,
    deploymentConfig: { ...cur.deploymentConfig, ...patch },
  }));
  res.json({ desk });
});

app.post('/api/desk/strategies/update', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id, patch } = req.body ?? {};
  const current = authoritativeDb.getOrCreateUserDesk(userId);
  const nextStrategies = current.strategies.map((s) => (s.id === id ? { ...s, ...patch } : s));
  const totalAllocated = nextStrategies.reduce((sum, s) => sum + s.allocatedInr, 0);

  if (totalAllocated > current.deploymentConfig.authorizedAllocationInr) {
    res.status(409).json({
      error: 'Total strategy allocations cannot exceed PIE Trader authorized capital.',
      desk: current,
    });
    return;
  }

  const desk = authoritativeDb.updateUserDesk(userId, (cur) => ({
    ...cur,
    strategies: nextStrategies,
  }));
  res.json({ desk });
});

app.post('/api/desk/automation/run-cycle', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const cur = authoritativeDb.getOrCreateUserDesk(userId);

  if (cur.deploymentConfig.automationMode === 'Analysis Only') {
    res.json({
      desk: cur,
      notice: {
        type: 'ok',
        text: 'Analysis Only mode: sizing calculated without reserving or placing orders.',
      },
    });
    return;
  }

  if (cur.deploymentConfig.automationMode === 'Live Automated') {
    const ready = checkLiveAutomationPrerequisites({
      config: cur.deploymentConfig,
      strategies: cur.strategies,
      brokers: cur.brokers,
      limits: cur.limits,
    });
    if (!ready.ready) {
      const msg = `Live Automated blocked: ${ready.missing.join('; ')}`;
      const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
        ...s,
        auditLogs: [
          {
            id: `aud-${Date.now()}`,
            timestamp: nowIst(),
            actor: 'RISK_MANAGER',
            action: 'LIVE_AUTO_BLOCKED',
            detail: msg,
            outcome: 'REJECTED',
          },
          ...s.auditLogs,
        ],
      }));
      res.status(409).json({ error: msg, desk });
      return;
    }
  }

  const picked = selectBestAutomatedOpportunity({
    opportunities: INITIAL_OPPORTUNITIES,
    strategies: cur.strategies,
    indices: INDICES_MASTER,
  });
  if (!picked.opportunity || !picked.strategy) {
    res.status(409).json({ error: picked.rejectionReason ?? 'No eligible opportunity found.', desk: cur });
    return;
  }

  const summary = getCapitalDeploymentSummary({
    config: cur.deploymentConfig,
    strategies: cur.strategies,
    reservations: cur.reservations,
    activePositions: cur.activePositions,
    ledger: cur.ledger,
  });
  const sizing = calculateStrategyLotSize({
    opportunity: picked.opportunity,
    strategy: picked.strategy,
    config: cur.deploymentConfig,
    summary,
    activePositions: cur.activePositions,
    limits: cur.limits,
  });
  const reservationResult = reserveCapitalAtomically({
    idempotencyKey: `auto-${picked.opportunity.id}-${Date.now()}`,
    strategy: picked.strategy,
    opportunity: picked.opportunity,
    lots: sizing.eligibleLots,
    sizing,
    config: cur.deploymentConfig,
    strategies: cur.strategies,
    reservations: cur.reservations,
    activePositions: cur.activePositions,
    ledger: cur.ledger,
    limits: cur.limits,
  });

  if (!reservationResult.ok || !reservationResult.reservation) {
    const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
      ...s,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'AUTO_RESERVATION_VETO',
          detail: reservationResult.reason ?? 'Blocked',
          outcome: 'REJECTED',
        },
        ...s.auditLogs,
      ],
    }));
    res.status(409).json({ error: reservationResult.reason ?? 'Auto execution blocked.', desk });
    return;
  }

  if (cur.deploymentConfig.automationMode === 'Paper Automation') {
    const fillResult = reconcilePartialOrFullFill({
      reservationId: reservationResult.reservation.id,
      filledLotsNow: reservationResult.reservation.requestedLots,
      fillPrice: picked.opportunity.contract.ltp,
      strategies: reservationResult.updatedStrategies,
      reservations: reservationResult.updatedReservations,
    });
    const newPos: ActivePosition = {
      id: `pos-auto-${Date.now()}`,
      index: picked.opportunity.index,
      tradingSymbol: picked.opportunity.contract.tradingSymbol,
      optionType: picked.opportunity.contract.optionType,
      expiry: picked.opportunity.contract.expiry,
      strike: picked.opportunity.contract.strike,
      lotSize: picked.opportunity.contract.lotSize,
      totalLots: reservationResult.reservation.requestedLots,
      avgEntryPrice: picked.opportunity.contract.ltp,
      currentLtp: picked.opportunity.contract.ltp,
      stopLoss: picked.opportunity.tradePlan.initialStopLoss,
      target1: picked.opportunity.tradePlan.target1,
      target2: picked.opportunity.tradePlan.target2,
      trailingMethod: picked.opportunity.tradePlan.trailingMethod,
      entryStages: [
        {
          stage: `Auto (${picked.strategy.name})`,
          lots: reservationResult.reservation.requestedLots,
          price: picked.opportunity.contract.ltp,
          timestamp: nowIst(),
        },
      ],
      realizedPnlInr: 0,
      mode: 'PAPER',
      openedAt: nowIst(),
    };
    const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
      ...s,
      strategies: fillResult.updatedStrategies,
      reservations: fillResult.updatedReservations,
      activePositions: [newPos, ...s.activePositions],
      ledger: {
        ...s.ledger,
        availableCapitalInr: Number((s.ledger.availableCapitalInr - fillResult.deployedDeltaInr).toFixed(2)),
        deployedCapitalInr: Number((s.ledger.deployedCapitalInr + fillResult.deployedDeltaInr).toFixed(2)),
        dailyTradesCount: s.ledger.dailyTradesCount + 1,
      },
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'TRADING_ORCHESTRATOR',
          action: 'AUTO_PAPER_EXECUTION',
          detail: `${picked.strategy!.name} sized ${reservationResult.reservation!.requestedLots} lot(s) ${picked.opportunity!.contract.tradingSymbol} (${sizing.limitingFactor}).`,
          outcome: 'APPROVED',
        },
        ...s.auditLogs,
      ],
    }));
    res.json({
      desk,
      notice: {
        type: 'ok',
        text: `Auto-executed ${reservationResult.reservation.requestedLots} lot(s) ${picked.opportunity.contract.tradingSymbol} under ${picked.strategy.name} (${sizing.limitingFactor}).`,
      },
    });
    return;
  }

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    strategies: reservationResult.updatedStrategies,
    reservations: reservationResult.updatedReservations,
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'TRADING_ORCHESTRATOR',
        action: 'CAPITAL_RESERVED_FOR_APPROVAL',
        detail: `Reserved ₹${reservationResult.reservation!.reservedCapitalInr.toLocaleString('en-IN')} (${reservationResult.reservation!.requestedLots} lots ${picked.opportunity!.contract.tradingSymbol}).`,
        outcome: 'INFO',
      },
      ...s.auditLogs,
    ],
  }));
  res.json({
    desk,
    notice: {
      type: 'ok',
      text: `Staged & reserved ${reservationResult.reservation.requestedLots} lot(s) ${picked.opportunity.contract.tradingSymbol}. Confirm Fill in Reservations queue.`,
    },
  });
});

app.post('/api/desk/reservations/:id/fill', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id } = req.params;
  const partialRatio = typeof req.body?.partialRatio === 'number' ? req.body.partialRatio : 1;
  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const r = cur.reservations.find((x) => x.id === id);
  if (!r) {
    res.status(404).json({ error: 'Reservation not found.', desk: cur });
    return;
  }

  const remainingLots = Math.max(0, r.requestedLots - r.filledLots);
  const fillLots =
    partialRatio < 1 ? Math.max(1, Math.floor(remainingLots * partialRatio)) : remainingLots;
  const opp = INITIAL_OPPORTUNITIES.find((o) => o.contract.tradingSymbol === r.tradingSymbol);
  const fillPrice = opp?.contract.ltp ?? 145;

  const fill = reconcilePartialOrFullFill({
    reservationId: id,
    filledLotsNow: fillLots,
    fillPrice,
    strategies: cur.strategies,
    reservations: cur.reservations,
  });

  const strat = cur.strategies.find((s) => s.id === r.strategyId);
  const newPos: ActivePosition = {
    id: `pos-res-${Date.now()}`,
    index: r.index,
    tradingSymbol: r.tradingSymbol,
    optionType: r.tradingSymbol.endsWith('PE') ? 'PE' : 'CE',
    expiry: opp?.contract.expiry ?? '20 Nov',
    strike: opp?.contract.strike ?? 24400,
    lotSize: r.lotSize,
    totalLots: fillLots,
    avgEntryPrice: fillPrice,
    currentLtp: fillPrice,
    stopLoss: opp?.tradePlan.initialStopLoss ?? Number((fillPrice * 0.84).toFixed(1)),
    target1: opp?.tradePlan.target1 ?? Number((fillPrice * 1.25).toFixed(1)),
    target2: opp?.tradePlan.target2 ?? Number((fillPrice * 1.48).toFixed(1)),
    trailingMethod: opp?.tradePlan.trailingMethod ?? '5m Structure Trail',
    entryStages: [
      {
        stage: `Auto (${strat?.name ?? 'Strategy'})`,
        lots: fillLots,
        price: fillPrice,
        timestamp: nowIst(),
      },
    ],
    realizedPnlInr: 0,
    mode: cur.deploymentConfig.automationMode === 'Live Automated' ? 'LIVE' : 'PAPER',
    openedAt: nowIst(),
  };

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    strategies: fill.updatedStrategies,
    reservations: fill.updatedReservations,
    activePositions: fillLots > 0 ? [newPos, ...s.activePositions] : s.activePositions,
    ledger: {
      ...s.ledger,
      availableCapitalInr: Number((s.ledger.availableCapitalInr - fill.deployedDeltaInr).toFixed(2)),
      deployedCapitalInr: Number((s.ledger.deployedCapitalInr + fill.deployedDeltaInr).toFixed(2)),
      dailyTradesCount: s.ledger.dailyTradesCount + 1,
    },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'TRADING_ORCHESTRATOR',
        action: 'RESERVATION_RECONCILED',
        detail: `Filled ${fillLots} lot(s) ${r.tradingSymbol} @ ₹${fillPrice}.`,
        outcome: 'APPROVED',
      },
      ...s.auditLogs,
    ],
  }));
  res.json({
    desk,
    notice: { type: 'ok', text: `Reconciled ${fillLots} lot(s) fill for ${r.tradingSymbol}.` },
  });
});

app.post('/api/desk/reservations/:id/release', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id } = req.params;
  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const rel = releaseOrRejectReservation({
    reservationId: id,
    outcome: 'Released',
    reason: 'Released by user / unexecuted order',
    strategies: cur.strategies,
    reservations: cur.reservations,
  });

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    strategies: rel.updatedStrategies,
    reservations: rel.updatedReservations,
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'RISK_MANAGER',
        action: 'RESERVATION_RELEASED',
        detail: `Released unused capital reservation ${id}.`,
        outcome: 'INFO',
      },
      ...s.auditLogs,
    ],
  }));
  res.json({
    desk,
    notice: { type: 'ok', text: 'Reservation released cleanly back to strategy allocation.' },
  });
});

app.post('/api/desk/orders/paper', (req: Request, res: Response) => {
  const { userId, session } = getAuthenticatedUserId(req);
  const opp = req.body?.opportunity as TradeOpportunity | undefined;
  if (!opp) {
    res.status(400).json({ error: 'Missing trade opportunity payload.' });
    return;
  }
  if (session?.sessionMode === 'READ_ONLY_LIVE') {
    res.status(403).json({
      error: 'Session is in Read-Only Intelligence mode. Switch to Paper Desk to simulate orders.',
    });
    return;
  }

  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const leg: BasketLeg = {
    id: `leg-direct-${Date.now()}`,
    kind: 'Initial Entry',
    index: opp.index,
    tradingSymbol: opp.contract.tradingSymbol,
    strike: opp.contract.strike,
    optionType: opp.contract.optionType,
    expiry: opp.contract.expiry,
    side: 'BUY',
    lots: Math.max(1, opp.tradePlan.initialLots),
    lotSize: opp.contract.lotSize,
    limitPrice: opp.contract.ltp,
    stopLoss: opp.tradePlan.initialStopLoss,
    target: opp.tradePlan.target1,
    conditionNote: opp.tradePlan.entryTrigger,
    fillStatus: 'Validated',
    filledLots: 0,
    avgFillPrice: 0,
  };

  const check = validateBasketWithRiskManager({
    legs: [leg],
    ledger: cur.ledger,
    limits: cur.limits,
    activePositions: cur.activePositions,
  });

  if (!check.approved) {
    const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
      ...s,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'PAPER_ORDER_VETOED',
          detail: check.reasons[0],
          outcome: 'REJECTED',
        },
        ...s.auditLogs,
      ],
    }));
    res.status(409).json({ error: check.reasons[0], desk });
    return;
  }

  const capitalUsed = leg.limitPrice * leg.lots * leg.lotSize;
  const newPos: ActivePosition = {
    id: `pos-${Date.now()}`,
    index: leg.index,
    tradingSymbol: leg.tradingSymbol,
    optionType: leg.optionType,
    expiry: leg.expiry,
    strike: leg.strike,
    lotSize: leg.lotSize,
    totalLots: leg.lots,
    avgEntryPrice: leg.limitPrice,
    currentLtp: leg.limitPrice,
    stopLoss: leg.stopLoss,
    target1: opp.tradePlan.target1,
    target2: opp.tradePlan.target2,
    trailingMethod: opp.tradePlan.trailingMethod,
    entryStages: [
      { stage: 'Initial Entry', lots: leg.lots, price: leg.limitPrice, timestamp: nowIst() },
    ],
    realizedPnlInr: 0,
    mode: 'PAPER',
    openedAt: nowIst(),
  };

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    activePositions: [newPos, ...s.activePositions],
    ledger: {
      ...s.ledger,
      availableCapitalInr: Number((s.ledger.availableCapitalInr - capitalUsed).toFixed(2)),
      deployedCapitalInr: Number((s.ledger.deployedCapitalInr + capitalUsed).toFixed(2)),
      dailyTradesCount: s.ledger.dailyTradesCount + 1,
    },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'TRADING_ORCHESTRATOR',
        action: 'PAPER_POSITION_OPENED',
        detail: `Filled ${leg.lots} lot(s) ${leg.tradingSymbol} @ ₹${leg.limitPrice}.`,
        outcome: 'APPROVED',
      },
      ...s.auditLogs,
    ],
  }));

  res.json({
    desk,
    notice: {
      type: 'ok',
      text: `Paper Trade filled: ${leg.lots} lot(s) ${leg.tradingSymbol} @ ₹${leg.limitPrice}.`,
    },
  });
});

app.post('/api/desk/basket/sync', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { basketLegs } = req.body ?? {};
  if (!Array.isArray(basketLegs)) {
    res.status(400).json({ error: 'Invalid basketLegs array.' });
    return;
  }
  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({ ...s, basketLegs }));
  res.json({ desk });
});

app.post('/api/desk/basket/execute', (req: Request, res: Response) => {
  const { userId, session } = getAuthenticatedUserId(req);
  if (session?.sessionMode === 'READ_ONLY_LIVE') {
    res.status(403).json({ error: 'Read-Only Intelligence mode blocks order simulation.' });
    return;
  }

  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const legsToRun: BasketLeg[] = Array.isArray(req.body?.basketLegs) ? req.body.basketLegs : cur.basketLegs;
  const check = validateBasketWithRiskManager({
    legs: legsToRun,
    ledger: cur.ledger,
    limits: cur.limits,
    activePositions: cur.activePositions,
  });

  if (!check.approved) {
    const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
      ...s,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'BASKET_VETOED',
          detail: check.reasons[0],
          outcome: 'REJECTED',
        },
        ...s.auditLogs,
      ],
    }));
    res.status(409).json({ error: check.reasons[0], desk });
    return;
  }

  const activeLegs = legsToRun.filter((l) => l.fillStatus !== 'Cancelled' && l.fillStatus !== 'Rejected');
  const firstLeg = activeLegs[0];
  const totalCapital = check.totalCapitalRequiredInr;

  const newPos: ActivePosition = {
    id: `pos-bsk-${Date.now()}`,
    index: firstLeg.index,
    tradingSymbol: firstLeg.tradingSymbol,
    optionType: firstLeg.optionType,
    expiry: firstLeg.expiry,
    strike: firstLeg.strike,
    lotSize: firstLeg.lotSize,
    totalLots: check.totalLots,
    avgEntryPrice: check.weightedAvgPremium,
    currentLtp: firstLeg.limitPrice,
    stopLoss: firstLeg.stopLoss,
    target1: firstLeg.target,
    target2: Number((firstLeg.target * 1.18).toFixed(1)),
    trailingMethod: '5m Structure Trail',
    entryStages: activeLegs.map((l) => ({
      stage: l.kind,
      lots: l.lots,
      price: l.limitPrice,
      timestamp: nowIst(),
    })),
    realizedPnlInr: 0,
    mode: 'PAPER',
    openedAt: nowIst(),
  };

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    activePositions: [newPos, ...s.activePositions],
    basketLegs: legsToRun.map((l) =>
      l.fillStatus === 'Cancelled' || l.fillStatus === 'Rejected'
        ? l
        : { ...l, fillStatus: 'Filled', filledLots: l.lots, avgFillPrice: l.limitPrice }
    ),
    ledger: {
      ...s.ledger,
      availableCapitalInr: Number((s.ledger.availableCapitalInr - totalCapital).toFixed(2)),
      deployedCapitalInr: Number((s.ledger.deployedCapitalInr + totalCapital).toFixed(2)),
      dailyTradesCount: s.ledger.dailyTradesCount + 1,
    },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'TRADING_ORCHESTRATOR',
        action: 'BASKET_EXECUTED_PAPER',
        detail: `Executed ${activeLegs.length}-leg basket (${check.totalLots} lots ${firstLeg.tradingSymbol}, weighted avg ₹${check.weightedAvgPremium}).`,
        outcome: 'APPROVED',
      },
      ...s.auditLogs,
    ],
  }));

  res.json({
    desk,
    notice: {
      type: 'ok',
      text: `Basket filled in Paper Desk: ${check.totalLots} lots @ avg ₹${check.weightedAvgPremium}.`,
    },
  });
});

app.post('/api/desk/positions/:id/scale', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id } = req.params;
  const addLots = Number(req.body?.addLots ?? 1);
  const confirmedBreakout = Boolean(req.body?.confirmedBreakout);
  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const pos = cur.activePositions.find((p) => p.id === id);

  if (!pos) {
    res.status(404).json({ error: 'Position not found.', desk: cur });
    return;
  }
  if (pos.currentLtp <= pos.avgEntryPrice) {
    const msg = `RISK VETO: Cannot add to losing/flat position ${pos.tradingSymbol} (LTP ₹${pos.currentLtp.toFixed(2)} <= Avg ₹${pos.avgEntryPrice.toFixed(2)}). Averaging down is strictly prohibited.`;
    const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
      ...s,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'ANTI_AVERAGING_VETO',
          detail: msg,
          outcome: 'REJECTED',
        },
        ...s.auditLogs,
      ],
    }));
    res.status(409).json({ error: msg, desk });
    return;
  }
  if (!confirmedBreakout) {
    const msg = 'Scale-in blocked: Predefined 5m BOS + volume confirmation condition is not satisfied.';
    res.status(409).json({ error: msg, desk: cur });
    return;
  }
  if (cur.limits.killSwitchEngaged || cur.limits.behaviouralCooldownActive) {
    res.status(409).json({ error: 'Execution lock is currently active.', desk: cur });
    return;
  }

  const addCost = pos.currentLtp * addLots * pos.lotSize;
  if (addCost > cur.ledger.availableCapitalInr) {
    res.status(409).json({ error: 'Insufficient available capital for scale-in.', desk: cur });
    return;
  }

  const newTotalLots = pos.totalLots + addLots;
  const newAvg = Number(
    ((pos.avgEntryPrice * pos.totalLots + pos.currentLtp * addLots) / newTotalLots).toFixed(2)
  );
  const revisedSl = Math.max(pos.stopLoss, Number((pos.avgEntryPrice * 0.98).toFixed(1)));

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    activePositions: s.activePositions.map((p) =>
      p.id === id
        ? {
            ...p,
            totalLots: newTotalLots,
            avgEntryPrice: newAvg,
            stopLoss: revisedSl,
            entryStages: [
              ...p.entryStages,
              {
                stage: `Add-on ${p.entryStages.length}`,
                lots: addLots,
                price: p.currentLtp,
                timestamp: nowIst(),
              },
            ],
          }
        : p
    ),
    ledger: {
      ...s.ledger,
      availableCapitalInr: Number((s.ledger.availableCapitalInr - addCost).toFixed(2)),
      deployedCapitalInr: Number((s.ledger.deployedCapitalInr + addCost).toFixed(2)),
    },
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'TRADING_ORCHESTRATOR',
        action: 'SCALE_IN_APPROVED',
        detail: `Added +${addLots} lot(s) ${pos.tradingSymbol} @ ₹${pos.currentLtp}. New avg ₹${newAvg}, SL raised to ₹${revisedSl}.`,
        outcome: 'APPROVED',
      },
      ...s.auditLogs,
    ],
  }));

  res.json({
    desk,
    notice: { type: 'ok', text: `Scaled in +${addLots} lot on ${pos.tradingSymbol}. Weighted Avg: ₹${newAvg}.` },
  });
});

app.post('/api/desk/positions/:id/ltp', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id } = req.params;
  const delta = Number(req.body?.delta ?? 0);
  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    activePositions: s.activePositions.map((p) =>
      p.id === id ? { ...p, currentLtp: Math.max(2, Number((p.currentLtp + delta).toFixed(2))) } : p
    ),
  }));
  res.json({ desk });
});

app.post('/api/desk/positions/:id/exit', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id } = req.params;
  const exitLots = Number(req.body?.exitLots ?? 1);
  const cur = authoritativeDb.getOrCreateUserDesk(userId);
  const pos = cur.activePositions.find((p) => p.id === id);

  if (!pos) {
    res.status(404).json({ error: 'Position not found.', desk: cur });
    return;
  }

  const lotsToClose = Math.min(pos.totalLots, exitLots);
  const costInfo = estimateOptionRoundTripCosts({
    entryPremium: pos.avgEntryPrice,
    exitPremium: pos.currentLtp,
    lots: lotsToClose,
    lotSize: pos.lotSize,
  });
  const netPnl = Number((costInfo.grossValueInr - costInfo.totalChargesInr).toFixed(2));
  const releasedPrincipal = pos.avgEntryPrice * lotsToClose * pos.lotSize;

  const desk = authoritativeDb.updateUserDesk(userId, (s) => {
    const stageLabel = pos.entryStages[0]?.stage ?? '';
    const autoPrefix = 'Auto (';
    const namedStrategy = stageLabel.startsWith(autoPrefix)
      ? stageLabel.slice(autoPrefix.length, -1)
      : null;
    let reconciled = false;
    const nextStrategies = s.strategies.map((st) => {
      if (reconciled || st.deployedInr <= 0) return st;
      const matchesName = namedStrategy ? st.name === namedStrategy : false;
      const matchesIndex = !namedStrategy && (st.index === pos.index || st.index === 'ALL');
      if (matchesName || matchesIndex) {
        reconciled = true;
        return {
          ...st,
          deployedInr: Math.max(0, Number((st.deployedInr - releasedPrincipal).toFixed(2))),
        };
      }
      return st;
    });

    const nextPositions =
      lotsToClose >= pos.totalLots
        ? s.activePositions.filter((p) => p.id !== id)
        : s.activePositions.map((p) =>
            p.id === id ? { ...p, totalLots: p.totalLots - lotsToClose } : p
          );

    return {
      ...s,
      strategies: nextStrategies,
      activePositions: nextPositions,
      ledger: {
        ...s.ledger,
        availableCapitalInr: Number((s.ledger.availableCapitalInr + releasedPrincipal + netPnl).toFixed(2)),
        deployedCapitalInr: Math.max(0, Number((s.ledger.deployedCapitalInr - releasedPrincipal).toFixed(2))),
        realizedPnlInr: Number((s.ledger.realizedPnlInr + netPnl).toFixed(2)),
        brokerageAndTaxesInr: Number((s.ledger.brokerageAndTaxesInr + costInfo.totalChargesInr).toFixed(2)),
        weeklyPnlInr: Number((s.ledger.weeklyPnlInr + netPnl).toFixed(2)),
        consecutiveLosses: netPnl < 0 ? s.ledger.consecutiveLosses + 1 : 0,
      },
      journal: [
        {
          id: `j-${Date.now()}`,
          timestamp: nowIst(),
          index: pos.index,
          tradingSymbol: pos.tradingSymbol,
          setup: pos.trailingMethod,
          plannedEntry: pos.entryStages[0]?.price ?? pos.avgEntryPrice,
          actualEntry: pos.avgEntryPrice,
          plannedSl: pos.stopLoss,
          actualSl: pos.stopLoss,
          lots: lotsToClose,
          netPnlInr: netPnl,
          ruleFollowed: true,
          notes: lotsToClose === pos.totalLots ? 'Full exit closed cleanly.' : `Partial exit (${lotsToClose} lot).`,
        },
        ...s.journal,
      ],
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'TRADING_ORCHESTRATOR',
          action: 'POSITION_EXITED',
          detail: `Closed ${lotsToClose} lot(s) ${pos.tradingSymbol} @ ₹${pos.currentLtp}. Net P&L after fees: ₹${netPnl}.`,
          outcome: 'APPROVED',
        },
        ...s.auditLogs,
      ],
    };
  });

  res.json({
    desk,
    notice: {
      type: 'ok',
      text: `Exited ${lotsToClose} lot(s) ${pos.tradingSymbol}. Net P&L: ${netPnl >= 0 ? '+' : ''}₹${netPnl}.`,
    },
  });
});

app.post('/api/desk/positions/square-off-all', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const desk = authoritativeDb.updateUserDesk(userId, (s) => {
    let available = s.ledger.availableCapitalInr;
    let deployed = s.ledger.deployedCapitalInr;
    let realized = s.ledger.realizedPnlInr;
    let charges = s.ledger.brokerageAndTaxesInr;
    let weekly = s.ledger.weeklyPnlInr;
    const newJournals = [...s.journal];

    for (const pos of s.activePositions) {
      const costInfo = estimateOptionRoundTripCosts({
        entryPremium: pos.avgEntryPrice,
        exitPremium: pos.currentLtp,
        lots: pos.totalLots,
        lotSize: pos.lotSize,
      });
      const netPnl = Number((costInfo.grossValueInr - costInfo.totalChargesInr).toFixed(2));
      const principal = pos.avgEntryPrice * pos.totalLots * pos.lotSize;
      available = Number((available + principal + netPnl).toFixed(2));
      deployed = Math.max(0, Number((deployed - principal).toFixed(2)));
      realized = Number((realized + netPnl).toFixed(2));
      charges = Number((charges + costInfo.totalChargesInr).toFixed(2));
      weekly = Number((weekly + netPnl).toFixed(2));
      newJournals.unshift({
        id: `j-sq-${Date.now()}-${pos.id}`,
        timestamp: nowIst(),
        index: pos.index,
        tradingSymbol: pos.tradingSymbol,
        setup: pos.trailingMethod,
        plannedEntry: pos.avgEntryPrice,
        actualEntry: pos.avgEntryPrice,
        plannedSl: pos.stopLoss,
        actualSl: pos.stopLoss,
        lots: pos.totalLots,
        netPnlInr: netPnl,
        ruleFollowed: true,
        notes: 'Emergency Square-Off All executed.',
      });
    }

    return {
      ...s,
      activePositions: [],
      strategies: s.strategies.map((st) => ({ ...st, deployedInr: 0 })),
      ledger: {
        ...s.ledger,
        availableCapitalInr: available,
        deployedCapitalInr: deployed,
        realizedPnlInr: realized,
        brokerageAndTaxesInr: charges,
        weeklyPnlInr: weekly,
      },
      limits: { ...s.limits, killSwitchEngaged: true },
      journal: newJournals,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'RISK_MANAGER',
          action: 'EMERGENCY_SQUARE_OFF_ALL',
          detail: `Squared off ${s.activePositions.length} open position(s) and engaged Kill Switch.`,
          outcome: 'REJECTED',
        },
        ...s.auditLogs,
      ],
    };
  });

  res.json({
    desk,
    notice: { type: 'warn', text: 'Emergency Square-Off completed & Kill Switch engaged.' },
  });
});

app.post('/api/desk/limits', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { profile, patch } = req.body ?? {};
  const desk = authoritativeDb.updateUserDesk(userId, (s) => {
    const nextLimits = profile
      ? { ...s.limits, ...RISK_PROFILE_PRESETS[profile as RiskProfile] }
      : { ...s.limits, ...(patch ?? {}) };
    return {
      ...s,
      limits: nextLimits,
      auditLogs: profile
        ? [
            {
              id: `aud-${Date.now()}`,
              timestamp: nowIst(),
              actor: 'RISK_MANAGER',
              action: 'PROFILE_UPDATED',
              detail: `Risk profile switched to ${profile}.`,
              outcome: 'INFO',
            },
            ...s.auditLogs,
          ]
        : s.auditLogs,
    };
  });
  res.json({ desk });
});

app.post('/api/desk/psychology/mood', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const mood = req.body?.mood as PsychologyLog['selfAssessmentMood'];
  const scoreMap: Record<PsychologyLog['selfAssessmentMood'], number> = {
    'Calm & Objective': 18,
    'Slightly Impatient': 42,
    'FOMO / Chasing': 74,
    Fatigued: 68,
  };
  const score = scoreMap[mood] ?? 25;
  const highRisk = score >= 65;

  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    psychology: {
      ...s.psychology,
      selfAssessmentMood: mood,
      behaviouralRiskScore: score,
    },
    limits: highRisk ? { ...s.limits, behaviouralCooldownActive: true } : s.limits,
  }));

  res.json({
    desk,
    notice: highRisk
      ? { type: 'warn', text: `Behavioural risk elevated (${mood}). Cooldown Lock engaged automatically.` }
      : undefined,
  });
});

app.post('/api/desk/brokers/toggle', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { id, action, perm } = req.body ?? {};
  const desk = authoritativeDb.updateUserDesk(userId, (s) => {
    const brokers = s.brokers.map((b: BrokerConnection) => {
      if (b.id !== id) return b;
      if (action === 'connection') {
        const nextStatus: BrokerConnection['authStatus'] =
          b.authStatus === 'Connected' ? 'Disconnected' : 'Connected';
        return {
          ...b,
          authStatus: nextStatus,
          marketDataPerm: nextStatus === 'Connected',
          portfolioPerm: nextStatus === 'Connected',
          orderPlacementPerm: false,
          liveExecutionEnabled: false,
          lastHeartbeat: nextStatus === 'Connected' ? nowIst() : 'Disconnected',
        };
      }
      if (action === 'permission' && perm) {
        return {
          ...b,
          [perm]: !b[perm as 'marketDataPerm' | 'portfolioPerm' | 'orderPlacementPerm'],
        };
      }
      return b;
    });
    return {
      ...s,
      brokers,
      auditLogs: [
        {
          id: `aud-${Date.now()}`,
          timestamp: nowIst(),
          actor: 'USER',
          action: 'BROKER_ADAPTER_UPDATED',
          detail: `Updated ${id} broker ${action}${perm ? ` (${perm})` : ''}.`,
          outcome: 'INFO',
        },
        ...s.auditLogs,
      ],
    };
  });
  res.json({ desk });
});

app.post('/api/desk/watchlist/toggle', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const oppId = String(req.body?.id ?? '');
  const desk = authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    watchlistIds: s.watchlistIds.includes(oppId)
      ? s.watchlistIds.filter((x) => x !== oppId)
      : [...s.watchlistIds, oppId],
  }));
  res.json({ desk });
});

// ---------- Collective Stories & Product Intelligence Governance ----------
app.post('/api/stories', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const body = req.body ?? {};
  const created = {
    id: `story-user-${Date.now()}`,
    scope: 'Internal PIE Desk' as const,
    traderAlias: String(body.traderAlias || 'PIE Desk Trader').slice(0, 50),
    regionOrDesk: String(body.regionOrDesk || 'NSE · Options'),
    marketFocus: String(body.marketFocus || 'NIFTY 50'),
    strategyUsed: String(body.strategyUsed || 'Intraday SMC'),
    capitalDeployedLabel: String(body.capitalDeployedLabel || 'Authorized Ring-Fenced Pool'),
    riskPerTradePct: Number(body.riskPerTradePct) || 1.0,
    winRatePct: Number(body.winRatePct) || 55,
    maxDrawdownPct: Number(body.maxDrawdownPct) || 3.0,
    netReturnLabel: String(body.netReturnLabel || '+8.0%'),
    adherenceScore: Number(body.adherenceScore) || 90,
    keyLesson: String(body.keyLesson || '').slice(0, 220),
    fullExperience: String(body.fullExperience || '').slice(0, 1000),
    verifiedByDesk: false,
    createdAt: 'Today',
  };

  if (!created.keyLesson || !created.fullExperience) {
    res.status(400).json({ error: 'Key lesson and full experience are required.' });
    return;
  }

  const stories = authoritativeDb.addSuccessStory(created);
  authoritativeDb.updateUserDesk(userId, (s) => ({
    ...s,
    auditLogs: [
      {
        id: `aud-${Date.now()}`,
        timestamp: nowIst(),
        actor: 'USER',
        action: 'EXPERIENCE_STORY_PUBLISHED',
        detail: `Published anonymized cohort experience "${created.traderAlias}" (${created.marketFocus}).`,
        outcome: 'INFO',
      },
      ...s.auditLogs,
    ],
  }));
  res.json({ stories });
});

app.post('/api/product/feedback', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const body = req.body ?? {};
  const title = String(body.title || '').trim();
  const description = String(body.description || '').trim();
  const category = (body.category as ProductFeedbackCategory) || 'Execution & Risk';
  if (!title || !description) {
    res.status(400).json({ error: 'Title and description are required.' });
    return;
  }
  const items = authoritativeDb.addProductFeedback({
    id: `fb-${Date.now()}`,
    title: title.slice(0, 120),
    category,
    description: description.slice(0, 700),
    submittedBy: String(body.submittedBy || userId),
    source: body.source === 'AI Chat Synthesis' ? 'AI Chat Synthesis' : 'User Submission',
    upvotes: 1,
    status: 'Under Review',
    createdAt: 'Today',
    testsVerified: false,
  });
  res.json({ productFeedback: items });
});

app.post('/api/product/feedback/:id/upvote', (req: Request, res: Response) => {
  const items = authoritativeDb.upvoteProductFeedback(req.params.id);
  res.json({ productFeedback: items });
});

app.post('/api/product/feedback/:id/transition', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { nextStatus, testsVerified, versionTag, note } = req.body ?? {};
  const result = authoritativeDb.transitionProductFeedback({
    id: req.params.id,
    nextStatus: nextStatus as ProductFeedbackStatus,
    actor: userId,
    testsVerified: typeof testsVerified === 'boolean' ? testsVerified : undefined,
    versionTag: typeof versionTag === 'string' ? versionTag : undefined,
    note: typeof note === 'string' ? note : undefined,
  });
  if (!result.ok) {
    res.status(409).json({ error: result.reason, productFeedback: result.items });
    return;
  }
  res.json({ productFeedback: result.items });
});

// ---------- Agentic GenAI Endpoints (Delegated to AgentOrchestrator & LlmGateway) ----------
app.post('/api/research-brief', async (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { opportunity, marketContext, globalNews, model } = req.body ?? {};
  if (!opportunity || !marketContext) {
    res.status(400).json({ error: 'Missing verified opportunity or marketContext payload.' });
    return;
  }

  try {
    const result = await tradingIntelligenceAgent.generateResearchBrief({
      userId,
      opportunity,
      marketContext,
      globalNews: Array.isArray(globalNews) ? globalNews : [],
      model,
    });
    res.json(result);
  } catch (err) {
    const status = (err as Error & { statusCode?: number }).statusCode || 502;
    const message = err instanceof Error ? err.message : 'Failed to generate AI research synthesis.';
    res.status(status).json({ error: message });
  }
});

app.post('/api/experience-chat', async (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { message, history = [], model = 'gemini-4-hydrogen', userDeskContext = {} } = req.body ?? {};
  if (!message || typeof message !== 'string' || !message.trim()) {
    res.status(400).json({ error: 'Please enter a trading experience or question to send.' });
    return;
  }

  try {
    const activeIndex = (userDeskContext?.activeIndex as IndexSymbol) || 'NIFTY 50';
    const result = await tradingIntelligenceAgent.handleExperienceChat({
      userId,
      message,
      history,
      model,
      activeIndex,
      userDeskContext,
    });
    res.json({
      ...result,
      telemetry: authoritativeDb.getTelemetrySnapshot(userId),
    });
  } catch (err) {
    const status = (err as Error & { statusCode?: number }).statusCode || 502;
    const messageErr = err instanceof Error ? err.message : 'Failed to reach TradingIntelligenceAgent.';
    res.status(status).json({ error: messageErr });
  }
});

app.post('/api/experience-insights', async (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  const { model = 'gemini-4-hydrogen' } = req.body ?? {};

  try {
    const result = await productDevelopmentAgent.synthesizeProductPriorities({
      userId,
      model,
    });
    res.json({
      ...result,
      telemetry: authoritativeDb.getTelemetrySnapshot(userId),
    });
  } catch (err) {
    const status = (err as Error & { statusCode?: number }).statusCode || 502;
    const messageErr = err instanceof Error ? err.message : 'Failed to synthesize product insights.';
    res.status(status).json({ error: messageErr });
  }
});

app.get('/api/ai/telemetry', (req: Request, res: Response) => {
  const { userId } = getAuthenticatedUserId(req);
  res.json(authoritativeDb.getTelemetrySnapshot(userId));
});

// ---------- External Backend Bridge (Java Gateway :8080, Risk Engine :8081, Python FastAPI :8000) ----------
app.post('/api/backend/probe', async (req: Request, res: Response) => {
  const config: BackendConnectionConfig = {
    javaGatewayUrl: String(req.body?.javaGatewayUrl || 'http://localhost:8080'),
    javaRiskEngineUrl: String(req.body?.javaRiskEngineUrl || 'http://localhost:8081'),
    pythonAnalyticsUrl: String(req.body?.pythonAnalyticsUrl || 'http://localhost:8000'),
    preferExternalData: Boolean(req.body?.preferExternalData),
  };
  const results = await probeBackendServices(config);
  res.json({ results });
});

app.post('/api/backend/sync', async (req: Request, res: Response) => {
  const config: BackendConnectionConfig = {
    javaGatewayUrl: String(req.body?.javaGatewayUrl || 'http://localhost:8080'),
    javaRiskEngineUrl: String(req.body?.javaRiskEngineUrl || 'http://localhost:8081'),
    pythonAnalyticsUrl: String(req.body?.pythonAnalyticsUrl || 'http://localhost:8000'),
    preferExternalData: Boolean(req.body?.preferExternalData),
  };
  const symbol = (req.body?.symbol as IndexSymbol) || 'NIFTY 50';
  const synced = await fetchSyncedMarketContracts(config, symbol);
  res.json(synced);
});

async function startServer() {
  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`PIE Trader server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer();
