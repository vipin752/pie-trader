import React, { useState } from 'react';
import {
  LayoutDashboard,
  Layers,
  Globe2,
  Briefcase,
  ShieldAlert,
  Link2,
  Sun,
  Moon,
  LogOut,
  Power,
  Menu,
  X,
  ChevronDown,
  Sparkles,
} from 'lucide-react';
import {
  IndexMasterSpec,
  IndexSymbol,
  PrimaryWorkspaceTab,
  RiskLimits,
  UserSession,
} from '../types/pie';

export interface InsideTabOption {
  id: string;
  label: string;
  description?: string;
}

interface WorkspaceShellProps {
  session: UserSession;
  onLogout: () => void;
  isDark: boolean;
  onToggleTheme: () => void;
  activeTab: PrimaryWorkspaceTab;
  onSelectTab: (tab: PrimaryWorkspaceTab) => void;
  insideTabs: InsideTabOption[];
  activeInsideTab: string;
  onSelectInsideTab: (id: string) => void;
  activeIndex: IndexSymbol;
  onSelectIndex: (index: IndexSymbol) => void;
  indices: Record<IndexSymbol, IndexMasterSpec>;
  netEquityInr: number;
  totalPnlInr: number;
  remainingRiskBudgetInr: number;
  limits: RiskLimits;
  onToggleKillSwitch: () => void;
  onOpenExperienceChat: () => void;
  isExperienceChatActive?: boolean;
  children: React.ReactNode;
}

const PRIMARY_NAV: {
  id: PrimaryWorkspaceTab;
  label: string;
  mobileLabel: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
}[] = [
  {
    id: 'dashboard',
    label: 'Dashboard',
    mobileLabel: 'Overview',
    description: 'Ranked index option setups, intraday price/SMC structure charts, sector rotation & market regime matrix.',
    icon: LayoutDashboard,
  },
  {
    id: 'options',
    label: 'Option Chain',
    mobileLabel: 'Options',
    description: 'Live CE/PE strike chain, Call/Put OI walls, Max Pain, and interactive 3D Implied Volatility surface.',
    icon: Layers,
  },
  {
    id: 'intelligence',
    label: 'Market Intel & News',
    mobileLabel: 'Intel',
    description: '7-Dimension decision pipeline, global-to-India macro news, probability calibration & Gen AI Experience Chat.',
    icon: Globe2,
  },
  {
    id: 'basket',
    label: 'Basket & Positions',
    mobileLabel: 'Basket',
    description: 'Stage multi-leg option baskets, enforce anti-averaging scaling, and manage or exit open positions.',
    icon: Briefcase,
  },
  {
    id: 'capital',
    label: 'Capital, Risk & Mind',
    mobileLabel: 'Risk',
    description: 'Ring-fence authorized capital, run automatic lot sizing, view 30-session P&L ledger, risk guardrails & psychology.',
    icon: ShieldAlert,
  },
  {
    id: 'brokers',
    label: 'Brokers & Compliance',
    mobileLabel: 'Brokers',
    description: 'Configure 2FA broker links, data vs order permissions, SEBI checklist & immutable decision audit trail.',
    icon: Link2,
  },
];

const INDEX_LIST: IndexSymbol[] = ['NIFTY 50', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY', 'SENSEX'];

export const WorkspaceShell: React.FC<WorkspaceShellProps> = ({
  session,
  onLogout,
  isDark,
  onToggleTheme,
  activeTab,
  onSelectTab,
  insideTabs,
  activeInsideTab,
  onSelectInsideTab,
  activeIndex,
  onSelectIndex,
  indices,
  netEquityInr,
  totalPnlInr,
  remainingRiskBudgetInr,
  limits,
  onToggleKillSwitch,
  onOpenExperienceChat,
  isExperienceChatActive = false,
  children,
}) => {
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const currentSpec = indices[activeIndex];

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-slate-50 dark:bg-[#0B1120] text-slate-900 dark:text-slate-100">
      {/* Desktop Left Sidebar */}
      <aside className="hidden lg:flex lg:flex-col lg:w-64 lg:shrink-0 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0F172A]">
        <div className="px-5 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-sky-600 dark:bg-sky-500 text-white font-display font-bold flex items-center justify-center text-sm">
            π
          </div>
          <span className="font-display font-bold text-base tracking-tight whitespace-nowrap">
            PIE Trader
          </span>
        </div>

        {/* Shared Active Index Quick Selector in Rail */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 space-y-2">
          <div className="text-xs font-medium text-slate-500 dark:text-slate-400">
            Shared Option Index
          </div>
          <div className="space-y-1">
            {INDEX_LIST.map((sym) => {
              const spec = indices[sym];
              const isSelected = sym === activeIndex;
              return (
                <button
                  key={sym}
                  type="button"
                  onClick={() => onSelectIndex(sym)}
                  data-tip-title={`${sym} (${spec.exchange})`}
                  data-tip={`Switch shared active index across all workspace tabs. Lot size ${spec.lotSize} · Regime: ${spec.regime} · PCR ${spec.pcr}.`}
                  className={`w-full px-3 py-2 rounded-lg text-left flex items-center justify-between text-xs transition-colors cursor-pointer ${
                    isSelected
                      ? 'bg-sky-500/12 text-sky-700 dark:text-sky-300 font-semibold'
                      : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/60'
                  }`}
                >
                  <span className="whitespace-nowrap">{sym}</span>
                  <span
                    className={`font-mono tabular-nums ${
                      spec.dayChange >= 0
                        ? 'text-sky-600 dark:text-sky-400'
                        : 'text-rose-600 dark:text-rose-400'
                    }`}
                  >
                    {spec.spot.toLocaleString('en-IN')}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Workspace Navigation */}
        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {PRIMARY_NAV.map((item) => {
            const Icon = item.icon;
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onSelectTab(item.id)}
                data-tip-title={item.label}
                data-tip={item.description}
                className={`w-full min-h-[42px] px-3 py-2 rounded-lg flex items-center gap-3 text-sm transition-colors cursor-pointer ${
                  active
                    ? 'bg-slate-900 dark:bg-sky-500/15 text-white dark:text-sky-300 font-medium'
                    : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/60 hover:text-slate-900 dark:hover:text-slate-100'
                }`}
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span className="truncate">{item.label}</span>
              </button>
            );
          })}
        </nav>

        {/* Account & Session Footer */}
        <div className="p-4 border-t border-slate-200 dark:border-slate-800 space-y-3 text-xs">
          <div className="flex items-center justify-between">
            <div className="min-w-0">
              <div className="font-semibold text-slate-900 dark:text-white truncate">
                {session.displayName}
              </div>
              <div className="text-slate-500 dark:text-slate-400 truncate">
                {session.riskProfile} · {session.sessionMode === 'PAPER_DESK' ? 'Paper Desk' : 'Read-Only'}
              </div>
            </div>
            <button
              type="button"
              onClick={onLogout}
              data-tip-title="Sign Out"
              data-tip="Exit your current PIE Trader desk session and return to the identity gate. Saved capital rules remain persisted."
              className="p-2 rounded-lg text-slate-500 hover:text-rose-500 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Column */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Desktop Top Bar (Single row, crisp metrics) */}
        <header className="hidden lg:flex items-center justify-between gap-6 px-6 py-3 border-b border-slate-200 dark:border-slate-800 bg-white/95 dark:bg-[#0F172A]/95 backdrop-blur-md sticky top-0 z-30">
          <div className="flex items-center gap-6 min-w-0 text-xs">
            <div className="flex items-center gap-2 whitespace-nowrap">
              <span className="font-display font-semibold text-sm text-slate-900 dark:text-white">
                {currentSpec.symbol}
              </span>
              <span className="font-mono tabular-nums font-semibold text-slate-800 dark:text-slate-200">
                {currentSpec.spot.toLocaleString('en-IN')}
              </span>
              <span
                className={`font-mono tabular-nums ${
                  currentSpec.dayChange >= 0
                    ? 'text-sky-600 dark:text-sky-400'
                    : 'text-rose-600 dark:text-rose-400'
                }`}
              >
                {currentSpec.dayChange >= 0 ? '+' : ''}
                {currentSpec.dayChangePct.toFixed(2)}%
              </span>
            </div>

            <span className="text-slate-300 dark:text-slate-700">|</span>

            <div className="flex items-center gap-5 text-slate-600 dark:text-slate-400 whitespace-nowrap">
              <span>
                Regime <strong className="text-slate-900 dark:text-slate-100 font-medium">{currentSpec.regime}</strong>
              </span>
              <span>
                VIX <strong className="font-mono tabular-nums text-slate-900 dark:text-slate-100">{currentSpec.indiaVix}</strong>
              </span>
              <span>
                Net Equity <strong className="font-mono tabular-nums text-slate-900 dark:text-slate-100">₹{netEquityInr.toLocaleString('en-IN')}</strong>
              </span>
              <span>
                Session P&L{' '}
                <strong
                  className={`font-mono tabular-nums ${
                    totalPnlInr >= 0 ? 'text-sky-600 dark:text-sky-400' : 'text-rose-600 dark:text-rose-400'
                  }`}
                >
                  {totalPnlInr >= 0 ? '+' : ''}₹{totalPnlInr.toLocaleString('en-IN')}
                </strong>
              </span>
              <span>
                Risk Allowance{' '}
                <strong className="font-mono tabular-nums text-sky-600 dark:text-sky-400">
                  ₹{Math.max(0, remainingRiskBudgetInr).toLocaleString('en-IN')}
                </strong>
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            <button
              type="button"
              onClick={onOpenExperienceChat}
              data-tip-title="AI Experience Coach (Gemini)"
              data-tip="Open the Gen AI chat to share your trading experience, compare with successful internal & global peers, and suggest product improvements."
              className={`min-h-[36px] px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
                isExperienceChatActive
                  ? 'bg-sky-600 text-white'
                  : 'border border-sky-500/40 text-sky-600 dark:text-sky-400 hover:bg-sky-500/10'
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>AI Coach</span>
            </button>

            <button
              type="button"
              onClick={onToggleKillSwitch}
              data-tip-title="Emergency Kill Switch"
              data-tip="Master safety circuit-breaker: immediately halts all new option orders, capital reservations, scale-ins, and automated execution across all strategies until manually released."
              className={`min-h-[36px] px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
                limits.killSwitchEngaged
                  ? 'bg-rose-600 text-white'
                  : 'border border-rose-500/40 text-rose-600 dark:text-rose-400 hover:bg-rose-500/10'
              }`}
            >
              <Power className="w-3.5 h-3.5" />
              <span>{limits.killSwitchEngaged ? 'Kill Switch Active' : 'Kill Switch'}</span>
            </button>

            <button
              type="button"
              onClick={onToggleTheme}
              aria-label="Toggle color theme"
              data-tip-title="Theme Switch"
              data-tip="Toggle between calm dark institutional desk mode and bright daylight mode."
              className="min-h-[36px] min-w-[36px] rounded-lg border border-slate-200 dark:border-slate-800 flex items-center justify-center text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            >
              {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
          </div>
        </header>

        {/* Mobile Compact Sticky Top Bar (52px height — respects 15% mobile sticky cap) */}
        <header className="lg:hidden sticky top-0 z-30 h-[52px] px-3.5 border-b border-slate-200 dark:border-slate-800 bg-white/95 dark:bg-[#0F172A]/95 backdrop-blur-md flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <div className="w-7 h-7 rounded-md bg-sky-600 text-white font-display font-bold flex items-center justify-center text-xs shrink-0">
              π
            </div>
            <div className="relative flex items-center">
              <select
                aria-label="Select option index"
                value={activeIndex}
                onChange={(e) => onSelectIndex(e.target.value as IndexSymbol)}
                className="appearance-none bg-transparent pr-5 font-display font-semibold text-sm text-slate-900 dark:text-white focus:outline-none cursor-pointer"
              >
                {INDEX_LIST.map((sym) => (
                  <option key={sym} value={sym} className="bg-white dark:bg-slate-900">
                    {sym}
                  </option>
                ))}
              </select>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 pointer-events-none -ml-4" />
            </div>
            <span className="font-mono text-xs tabular-nums text-slate-500 dark:text-slate-400 truncate">
              {currentSpec.spot.toLocaleString('en-IN')}
            </span>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <span
              className={`font-mono text-xs font-semibold tabular-nums px-2 py-1 ${
                totalPnlInr >= 0 ? 'text-sky-600 dark:text-sky-400' : 'text-rose-600 dark:text-rose-400'
              }`}
            >
              {totalPnlInr >= 0 ? '+' : ''}₹{totalPnlInr.toLocaleString('en-IN')}
            </span>
            <button
              type="button"
              onClick={onOpenExperienceChat}
              aria-label="Open AI Experience Chat"
              className={`min-h-[40px] min-w-[40px] flex items-center justify-center rounded-lg ${
                isExperienceChatActive
                  ? 'bg-sky-600 text-white'
                  : 'text-sky-600 dark:text-sky-400'
              }`}
            >
              <Sparkles className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={onToggleTheme}
              aria-label="Toggle theme"
              className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-lg text-slate-600 dark:text-slate-300"
            >
              {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            </button>
            <button
              type="button"
              onClick={() => setMobileSheetOpen(true)}
              aria-label="Open workspace menu"
              className="min-h-[40px] min-w-[40px] flex items-center justify-center rounded-lg text-slate-700 dark:text-slate-200"
            >
              <Menu className="w-5 h-5" />
            </button>
          </div>
        </header>

        {/* Shared Inside Tab Bar (Synchronized with active workspace & active index) */}
        <div className="border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0F172A]/60 px-4 lg:px-6">
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-1 overflow-x-auto no-scrollbar py-2">
              {insideTabs.map((tab) => {
                const isActive = tab.id === activeInsideTab;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => onSelectInsideTab(tab.id)}
                    data-tip-title={tab.label}
                    data-tip={tab.description || `Open ${tab.label} view synchronized with ${activeIndex}.`}
                    className={`min-h-[38px] lg:min-h-[34px] px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap shrink-0 transition-colors cursor-pointer ${
                      isActive
                        ? 'bg-sky-600 text-white dark:bg-sky-500/20 dark:text-sky-300'
                        : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800/60'
                    }`}
                  >
                    {tab.label}
                  </button>
                );
              })}
            </div>

            <div className="hidden md:flex items-center gap-3 text-[11px] text-slate-500 dark:text-slate-400 whitespace-nowrap shrink-0">
              <span>Shared Context: {activeIndex}</span>
              <span aria-hidden="true">·</span>
              <span>{currentSpec.expiries[0]} Expiry</span>
            </div>
          </div>
        </div>

        {/* Main Viewport */}
        <main className="flex-1 p-4 sm:p-6 pb-24 lg:pb-8 max-w-[1380px] w-full mx-auto">
          {children}
        </main>
      </div>

      {/* Mobile Fixed Bottom Tab Bar (56px height, ergonomic thumb-zone) */}
      <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-40 h-14 pb-safe bg-white/95 dark:bg-[#0F172A]/95 backdrop-blur-md border-t border-slate-200 dark:border-slate-800 grid grid-cols-5">
        {PRIMARY_NAV.slice(0, 4).map((item) => {
          const Icon = item.icon;
          const active = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelectTab(item.id)}
              className={`flex flex-col items-center justify-center gap-0.5 min-h-[44px] transition-colors ${
                active
                  ? 'text-sky-600 dark:text-sky-400 font-semibold'
                  : 'text-slate-500 dark:text-slate-400'
              }`}
            >
              <Icon className="w-[18px] h-[18px]" />
              <span className="text-[10px] leading-none">{item.mobileLabel}</span>
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => setMobileSheetOpen(true)}
          className={`flex flex-col items-center justify-center gap-0.5 min-h-[44px] transition-colors ${
            activeTab === 'capital' || activeTab === 'brokers'
              ? 'text-sky-600 dark:text-sky-400 font-semibold'
              : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          <ShieldAlert className="w-[18px] h-[18px]" />
          <span className="text-[10px] leading-none">Desk</span>
        </button>
      </nav>

      {/* Mobile Bottom Sheet Drawer for Desk / Capital / Risk / Brokers / Kill Switch */}
      {mobileSheetOpen && (
        <div
          className="lg:hidden fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end"
          onClick={() => setMobileSheetOpen(false)}
        >
          <div
            className="w-full rounded-t-3xl bg-white dark:bg-[#111827] border-t border-slate-200 dark:border-slate-800 p-5 pb-8 space-y-4 max-h-[85vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="w-10 h-1.5 bg-slate-300 dark:bg-slate-700 rounded-full mx-auto" />
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-display font-bold text-base text-slate-900 dark:text-white">
                  {session.displayName} · PIE Desk
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {session.riskProfile} Profile · Net Equity ₹{netEquityInr.toLocaleString('en-IN')}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setMobileSheetOpen(false)}
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-slate-500"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Shared Index Pills */}
            <div className="space-y-1.5">
              <div className="text-xs font-medium text-slate-500 dark:text-slate-400">Switch Shared Index</div>
              <div className="flex flex-wrap gap-2">
                {INDEX_LIST.map((sym) => (
                  <button
                    key={sym}
                    type="button"
                    onClick={() => {
                      onSelectIndex(sym);
                      setMobileSheetOpen(false);
                    }}
                    className={`min-h-[40px] px-3 py-1.5 rounded-lg text-xs font-medium border ${
                      activeIndex === sym
                        ? 'border-sky-500 bg-sky-500/15 text-sky-600 dark:text-sky-300'
                        : 'border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-300'
                    }`}
                  >
                    {sym}
                  </button>
                ))}
              </div>
            </div>

            {/* All Workspaces */}
            <div className="space-y-1.5">
              <div className="text-xs font-medium text-slate-500 dark:text-slate-400">Modules</div>
              <div className="grid grid-cols-1 gap-1.5">
                {PRIMARY_NAV.map((item) => {
                  const Icon = item.icon;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => {
                        onSelectTab(item.id);
                        setMobileSheetOpen(false);
                      }}
                      className={`min-h-[46px] px-3.5 rounded-xl flex items-center gap-3 text-sm text-left ${
                        activeTab === item.id
                          ? 'bg-sky-500/15 text-sky-600 dark:text-sky-300 font-semibold'
                          : 'bg-slate-50 dark:bg-[#0B1120] text-slate-700 dark:text-slate-300'
                      }`}
                    >
                      <Icon className="w-4 h-4 shrink-0" />
                      <span>{item.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Emergency Kill Switch & Logout */}
            <div className="grid grid-cols-2 gap-2.5 pt-2">
              <button
                type="button"
                onClick={onToggleKillSwitch}
                data-tip-title="Emergency Kill Switch"
                data-tip="Master safety lock: stops all new orders, capital reservations, and automated execution immediately."
                className={`min-h-[46px] px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 ${
                  limits.killSwitchEngaged
                    ? 'bg-rose-600 text-white'
                    : 'border border-rose-500/40 text-rose-600 dark:text-rose-400'
                }`}
              >
                <Power className="w-4 h-4" />
                <span>{limits.killSwitchEngaged ? 'Disengage Kill' : 'Kill Switch'}</span>
              </button>
              <button
                type="button"
                onClick={onLogout}
                data-tip="Sign out of your active trading desk session."
                className="min-h-[46px] px-3 rounded-xl border border-slate-200 dark:border-slate-700 text-xs font-semibold flex items-center justify-center gap-2 text-slate-700 dark:text-slate-300"
              >
                <LogOut className="w-4 h-4" />
                <span>Sign Out</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
