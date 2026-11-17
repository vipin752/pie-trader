import React, { useEffect, useState } from 'react';
import {
  Lock,
  ShieldCheck,
  Sun,
  Moon,
  ArrowRight,
  CheckCircle2,
  Loader2,
  KeyRound,
  UserPlus,
  LogIn,
} from 'lucide-react';
import { AuthProviderId, AuthProviderInfo, RiskProfile, UserDeskState, UserSession } from '../types/pie';
import { INDICES_MASTER } from '../data/pieMarketData';
import { pieApi, setStoredSessionToken } from '../services/pieApiClient';

interface LoginViewProps {
  onLogin: (session: UserSession, desk?: UserDeskState) => void;
  isDark: boolean;
  onToggleTheme: () => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onLogin, isDark, onToggleTheme }) => {
  const [authMode, setAuthMode] = useState<'signin' | 'register'>('signin');
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [totp, setTotp] = useState('');
  const [enableTotp, setEnableTotp] = useState(false);
  const [totpSetupInfo, setTotpSetupInfo] = useState<{
    secret: string;
    currentCodePreview: string;
    otpauthUri: string;
    pendingSession: UserSession;
    pendingDesk: UserDeskState;
  } | null>(null);

  const [riskProfile, setRiskProfile] = useState<RiskProfile>('Conservative');
  const [sessionMode, setSessionMode] = useState<'PAPER_DESK' | 'READ_ONLY_LIVE'>('PAPER_DESK');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [oauthBusy, setOauthBusy] = useState<AuthProviderId | null>(null);
  const [providers, setProviders] = useState<AuthProviderInfo[]>([]);

  useEffect(() => {
    let active = true;
    pieApi
      .getAuthProviders()
      .then((res) => {
        if (active) setProviders(res.providers || []);
      })
      .catch(() => {
        if (active) setProviders([]);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      const origin = event.origin || '';
      if (!origin.endsWith('.run.app') && !origin.includes('localhost') && !origin.includes('127.0.0.1')) {
        return;
      }
      if (event.data?.type === 'PIE_OAUTH_SUCCESS' && event.data?.token && event.data?.session) {
        setOauthBusy(null);
        setStoredSessionToken(String(event.data.token));
        onLogin(event.data.session as UserSession);
      } else if (event.data?.type === 'PIE_OAUTH_ERROR') {
        setOauthBusy(null);
        setError(String(event.data.error || 'Client OAuth sign-in was cancelled or failed.'));
      }
    };
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [onLogin]);

  const configuredOAuthProviders = providers.filter((p) => p.id !== 'email' && p.configured);
  const unconfiguredOAuthProviders = providers.filter((p) => p.id !== 'email' && !p.configured);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !email.includes('@')) {
      setError('Enter a valid desk email address.');
      return;
    }
    const minLen = authMode === 'register' ? 6 : 1;
    if (password.length < minLen) {
      setError(
        authMode === 'register'
          ? 'Passphrase must be at least 6 characters.'
          : 'Enter your desk passphrase.'
      );
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      if (authMode === 'register') {
        const res = await pieApi.register({
          email: email.trim(),
          password,
          displayName: displayName.trim() || undefined,
          enableTotp,
          riskProfile,
          sessionMode,
        });
        if (res.totpSetup) {
          setTotpSetupInfo({
            ...res.totpSetup,
            pendingSession: res.session,
            pendingDesk: res.desk,
          });
          return;
        }
        onLogin(res.session, res.desk);
      } else {
        const res = await pieApi.login({
          email: email.trim(),
          password,
          totp: totp.trim(),
          riskProfile,
          sessionMode,
        });
        onLogin(res.session, res.desk);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to authenticate with PIE Desk server.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleOAuthLogin = async (providerId: Exclude<AuthProviderId, 'email'>) => {
    setError('');
    setOauthBusy(providerId);
    try {
      const { url } = await pieApi.getOAuthUrl(providerId, riskProfile, sessionMode);
      const popup = window.open(url, 'pie_oauth_popup', 'width=560,height=680');
      if (!popup) {
        setOauthBusy(null);
        setError('Popup was blocked by browser. Allow popups for this site to continue with OAuth.');
      }
    } catch (err) {
      setOauthBusy(null);
      setError(err instanceof Error ? err.message : `Failed to start ${providerId} OAuth sign-in.`);
    }
  };

  const quickIndexPreview = [
    INDICES_MASTER['NIFTY 50'],
    INDICES_MASTER.BANKNIFTY,
    INDICES_MASTER.FINNIFTY,
    INDICES_MASTER.SENSEX,
  ];

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-100 transition-colors">
      {/* Clean Top Bar */}
      <header className="w-full border-b border-slate-200 dark:border-slate-800/80 px-5 sm:px-8 py-4 flex items-center justify-between gap-8">
        <div className="flex items-center gap-3 whitespace-nowrap shrink-0">
          <div className="w-8 h-8 rounded-lg bg-sky-600 dark:bg-sky-500 text-white font-display font-bold flex items-center justify-center text-sm tracking-tight">
            π
          </div>
          <span className="font-display font-bold text-lg tracking-tight">PIE Trader</span>
        </div>

        <div className="flex items-center gap-4 shrink-0">
          <span className="hidden sm:inline text-xs text-slate-500 dark:text-slate-400">
            NSE · BSE Index Options Intelligence
          </span>
          <button
            type="button"
            onClick={onToggleTheme}
            aria-label="Toggle color theme"
            data-tip="Switch between dark institutional desk theme and daylight light theme."
            className="min-h-[44px] min-w-[44px] sm:min-h-[38px] sm:min-w-[38px] rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
          >
            {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>
        </div>
      </header>

      {/* Main Split Content */}
      <main className="flex-1 w-full max-w-6xl mx-auto px-4 sm:px-8 py-8 lg:py-12 grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-center">
        {/* Left: Calm Institutional Philosophy & Index Snapshot */}
        <section className="lg:col-span-7 space-y-8 order-2 lg:order-1">
          <div className="space-y-4">
            <p className="text-xs font-medium tracking-wide text-sky-600 dark:text-sky-400">
              Probability · Intelligence · Discipline · Capital Efficiency
            </p>
            <h1 className="font-display text-3xl sm:text-4xl lg:text-[42px] font-bold tracking-tight leading-tight text-slate-900 dark:text-white text-balance">
              Decision-first index options intelligence built to protect capital.
            </h1>
            <p className="text-sm sm:text-base text-slate-600 dark:text-slate-400 leading-relaxed max-w-2xl">
              Evaluate global cues, RBI macro context, ICT market structure, options positioning, and out-of-sample probability before sizing a single lot. Authoritative backend engines enforce every limit automatically.
            </p>
          </div>

          {/* Live Index Snapshot Grid (Blue = upside/profit, Red = downside/loss) */}
          <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
              <span>Supported Indian Indices · Session 09:15–15:30 IST</span>
              <span className="font-mono tabular-nums">VIX 13.42</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 divide-y sm:divide-y-0 sm:divide-x divide-slate-200 dark:divide-slate-800">
              {quickIndexPreview.map((idx) => (
                <div key={idx.symbol} className="p-4 space-y-1">
                  <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    {idx.symbol}
                  </div>
                  <div className="font-mono text-base font-semibold tabular-nums text-slate-900 dark:text-white">
                    {idx.spot.toLocaleString('en-IN')}
                  </div>
                  <div
                    className={`font-mono text-xs tabular-nums ${
                      idx.dayChange >= 0
                        ? 'text-sky-600 dark:text-sky-400'
                        : 'text-rose-600 dark:text-rose-400'
                    }`}
                  >
                    {idx.dayChange >= 0 ? '+' : ''}
                    {idx.dayChangePct.toFixed(2)}% · {idx.regime.split(' ')[0]}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Key Architectural Guarantees */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs text-slate-600 dark:text-slate-400">
            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
              <span>Client identity login (Email/OAuth) stays separate from broker trading auth.</span>
            </div>
            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
              <span>Net P&L deducts STT, exchange fees, brokerage, GST, and spread slippage.</span>
            </div>
            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
              <span>Hard RiskManager veto prevents averaging down on losing option legs.</span>
            </div>
          </div>
        </section>

        {/* Right: Comfortable Login Form */}
        <section className="lg:col-span-5 order-1 lg:order-2">
          <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#111827] p-6 sm:p-8 shadow-sm">
            <div className="space-y-1.5 mb-5">
              <div className="flex items-center gap-2 text-xs font-medium text-slate-500 dark:text-slate-400">
                <Lock className="w-3.5 h-3.5 text-sky-500" />
                <span>PIE Identity Gate · Separate from Broker Auth</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <h2 className="font-display text-xl font-bold text-slate-900 dark:text-white">
                  {authMode === 'signin' ? 'Sign in to Trading Desk' : 'Create Desk Account'}
                </h2>
                <div className="inline-flex rounded-lg p-0.5 bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800 text-xs">
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('signin');
                      setError('');
                    }}
                    className={`px-2.5 py-1.5 rounded-md font-medium flex items-center gap-1 transition-colors cursor-pointer ${
                      authMode === 'signin'
                        ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  >
                    <LogIn className="w-3 h-3" />
                    <span>Sign In</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setAuthMode('register');
                      setError('');
                    }}
                    className={`px-2.5 py-1.5 rounded-md font-medium flex items-center gap-1 transition-colors cursor-pointer ${
                      authMode === 'register'
                        ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                        : 'text-slate-500 dark:text-slate-400'
                    }`}
                  >
                    <UserPlus className="w-3 h-3" />
                    <span>Register</span>
                  </button>
                </div>
              </div>
            </div>

            {totpSetupInfo ? (
              <div className="space-y-4 rounded-xl border border-sky-500/30 bg-sky-500/5 p-4 text-xs">
                <div className="flex items-center gap-2 font-semibold text-sky-600 dark:text-sky-400">
                  <KeyRound className="w-4 h-4" />
                  <span>Save Your 2FA TOTP Authenticator Secret</span>
                </div>
                <p className="text-slate-600 dark:text-slate-300 leading-relaxed">
                  Add this Base32 key to Google Authenticator, Microsoft Authenticator, or Bitwarden before entering the desk:
                </p>
                <div className="p-2.5 rounded-lg bg-slate-900 text-sky-300 font-mono break-all select-all">
                  {totpSetupInfo.secret}
                </div>
                <div className="text-[11px] text-slate-500 dark:text-slate-400">
                  Current verification code check: <span className="font-mono text-slate-900 dark:text-white">{totpSetupInfo.currentCodePreview}</span>
                </div>
                <button
                  type="button"
                  onClick={() => onLogin(totpSetupInfo.pendingSession, totpSetupInfo.pendingDesk)}
                  className="w-full min-h-[44px] rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-semibold text-sm flex items-center justify-center gap-2 cursor-pointer"
                >
                  <span>I Saved My 2FA Key — Enter Workspace</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            ) : (
              <>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                      Desk Email
                    </label>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full min-h-[44px] px-3.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-sky-500"
                      placeholder="trader@firm.in"
                      autoComplete="email"
                    />
                  </div>

                  {authMode === 'register' && (
                    <div>
                      <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                        Trader Display Name (Optional)
                      </label>
                      <input
                        type="text"
                        value={displayName}
                        onChange={(e) => setDisplayName(e.target.value)}
                        className="w-full min-h-[44px] px-3.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-sky-500"
                        placeholder="e.g. Vipin Dube"
                      />
                    </div>
                  )}

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                        Passphrase
                      </label>
                      <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder={authMode === 'register' ? 'Min 6 characters' : 'Enter passphrase'}
                        autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                        className="w-full min-h-[44px] px-3.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] text-sm text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-sky-500"
                      />
                    </div>
                    {authMode === 'signin' ? (
                      <div>
                        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                          2FA TOTP Pin (If enabled)
                        </label>
                        <input
                          type="text"
                          maxLength={6}
                          value={totp}
                          onChange={(e) => setTotp(e.target.value)}
                          className="w-full min-h-[44px] px-3.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] text-sm font-mono tabular-nums text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-sky-500"
                          placeholder="6-digit PIN"
                        />
                      </div>
                    ) : (
                      <div className="flex items-end pb-1">
                        <label className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300 cursor-pointer select-none min-h-[44px]">
                          <input
                            type="checkbox"
                            checked={enableTotp}
                            onChange={(e) => setEnableTotp(e.target.checked)}
                            className="rounded border-slate-400 text-sky-600 focus:ring-sky-500"
                          />
                          <span>Enable RFC 6238 2FA TOTP</span>
                        </label>
                      </div>
                    )}
                  </div>

                  {/* Risk Profile Selector */}
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                      Capital Risk Profile (Default: Conservative)
                    </label>
                    <div className="grid grid-cols-3 gap-1.5 p-1 rounded-lg bg-slate-100 dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800">
                      {(['Conservative', 'Moderate', 'Aggressive'] as RiskProfile[]).map((profile) => {
                        const active = riskProfile === profile;
                        const profileTip =
                          profile === 'Conservative'
                            ? '0.5% max risk per trade, tight 1.8% spread limit & strict ₹12,000 daily loss cap.'
                            : profile === 'Moderate'
                            ? '1.0% max risk per trade with balanced exposure limits.'
                            : '1.75% max risk per trade for high-conviction sessions.';
                        return (
                          <button
                            key={profile}
                            type="button"
                            onClick={() => setRiskProfile(profile)}
                            data-tip-title={`${profile} Risk Profile`}
                            data-tip={profileTip}
                            className={`min-h-[38px] px-2 text-xs font-medium rounded-md transition-colors whitespace-nowrap shrink-0 ${
                              active
                                ? 'bg-white dark:bg-slate-800 text-slate-900 dark:text-white shadow-xs'
                                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                            }`}
                          >
                            {profile}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* Execution Guard Mode */}
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1.5">
                      Session Authorization Scope
                    </label>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setSessionMode('PAPER_DESK')}
                        data-tip-title="Paper Trading Desk"
                        data-tip="Simulate single-leg trades, lot sizing, and exits using virtual capital without risking real funds."
                        className={`min-h-[44px] px-3 py-2 rounded-lg border text-left text-xs transition-colors ${
                          sessionMode === 'PAPER_DESK'
                            ? 'border-sky-500 bg-sky-500/10 text-sky-700 dark:text-sky-300'
                            : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400'
                        }`}
                      >
                        <div className="font-semibold">Paper Trading Desk</div>
                        <div className="text-[11px] opacity-80">Simulated fills + live chain</div>
                      </button>
                      <button
                        type="button"
                        onClick={() => setSessionMode('READ_ONLY_LIVE')}
                        data-tip-title="Read-Only Intelligence"
                        data-tip="Inspect option chains, 7D signals, AI Coach & risk checks while blocking all order execution."
                        className={`min-h-[44px] px-3 py-2 rounded-lg border text-left text-xs transition-colors ${
                          sessionMode === 'READ_ONLY_LIVE'
                            ? 'border-sky-500 bg-sky-500/10 text-sky-700 dark:text-sky-300'
                            : 'border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400'
                        }`}
                      >
                        <div className="font-semibold">Read-Only Intelligence</div>
                        <div className="text-[11px] opacity-80">Research & risk analysis only</div>
                      </button>
                    </div>
                  </div>

                  {error && (
                    <p className="text-xs text-rose-600 dark:text-rose-400 font-medium">{error}</p>
                  )}

                  <button
                    type="submit"
                    disabled={submitting}
                    data-tip={
                      authMode === 'register'
                        ? 'Create a verified scrypt-protected PIE Desk account.'
                        : 'Authenticate against PIE Desk (/api/auth/login) with isolated user ledger.'
                    }
                    className="w-full min-h-[48px] px-4 py-3 rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-semibold text-sm flex items-center justify-center gap-2 transition-colors cursor-pointer disabled:opacity-60"
                  >
                    {submitting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>
                          {authMode === 'register'
                            ? 'Creating Desk Account...'
                            : 'Authenticating Desk Session...'}
                        </span>
                      </>
                    ) : (
                      <>
                        <span>
                          {authMode === 'register' ? 'Create Account & Enter Desk' : 'Enter PIE Workspace'}
                        </span>
                        <ArrowRight className="w-4 h-4" />
                      </>
                    )}
                  </button>
                </form>

                {/* Configured Client OAuth Providers (Only shown when actually configured & functional on backend) */}
                {configuredOAuthProviders.length > 0 && (
                  <div className="mt-5 pt-4 border-t border-slate-200 dark:border-slate-800 space-y-2.5">
                    <div className="text-[11px] font-medium text-slate-500 dark:text-slate-400 text-center">
                      Or authenticate via configured Client OAuth 2.0
                    </div>
                    <div className="grid grid-cols-1 gap-2">
                      {configuredOAuthProviders.map((prov) => {
                        const providerId = prov.id as Exclude<AuthProviderId, 'email'>;
                        const busy = oauthBusy === providerId;
                        return (
                          <button
                            key={prov.id}
                            type="button"
                            disabled={Boolean(oauthBusy)}
                            onClick={() => handleOAuthLogin(providerId)}
                            data-tip={prov.description}
                            className="w-full min-h-[42px] px-3.5 py-2 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-[#0B1120] hover:bg-slate-100 dark:hover:bg-slate-800 text-xs font-semibold text-slate-800 dark:text-slate-100 flex items-center justify-center gap-2 transition-colors cursor-pointer disabled:opacity-60"
                          >
                            {busy ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <ShieldCheck className="w-3.5 h-3.5 text-sky-500" />
                            )}
                            <span>Continue with {prov.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {unconfiguredOAuthProviders.length > 0 && (
                  <div className="mt-4 pt-3 border-t border-slate-200/70 dark:border-slate-800/70 text-[11px] text-slate-500 dark:text-slate-400 leading-relaxed">
                    Client OAuth ready when configured in backend env:{' '}
                    {unconfiguredOAuthProviders.map((p) => p.label).join(' · ')}.
                  </div>
                )}
              </>
            )}

            <div className="pt-3 mt-3 border-t border-slate-200/60 dark:border-slate-800/60 flex flex-wrap items-center justify-between gap-2 text-[11px] text-slate-500 dark:text-slate-400">
              <span className="flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-500" />
                Strict HMAC/PKCE Auth Enforced
              </span>
              <span>Broker 2FA gated separately</span>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};
