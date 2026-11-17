import {
  ActivePosition,
  BrokerConnection,
  CapitalDeploymentConfig,
  CapitalLedger,
  CapitalReservation,
  IndexMasterSpec,
  IndexSymbol,
  LotSizingBreakdown,
  RiskLimits,
  StrategyAllocation,
  TradeOpportunity,
} from '../types/pie';
import { estimateOptionRoundTripCosts } from './pieEngine';

export const EXCHANGE_FREEZE_LOTS: Record<IndexSymbol, number> = {
  'NIFTY 50': 24,
  BANKNIFTY: 30,
  FINNIFTY: 27,
  MIDCPNIFTY: 23,
  SENSEX: 50,
};

export interface CapitalDeploymentSummary {
  totalDeclaredCapitalInr: number;
  authorizedAllocationInr: number;
  undeployedNotAuthorizedInr: number;
  deployedCapitalInr: number;
  reservedCapitalInr: number;
  availableAllocationInr: number;
  totalStrategyAllocatedInr: number;
  unassignedAuthorizedInr: number;
  openPositionsCount: number;
  openPositionRiskInr: number;
  remainingDailyRiskInr: number;
}

export function getCapitalDeploymentSummary(params: {
  config: CapitalDeploymentConfig;
  strategies: StrategyAllocation[];
  reservations: CapitalReservation[];
  activePositions: ActivePosition[];
  ledger: CapitalLedger;
}): CapitalDeploymentSummary {
  const { config, strategies, reservations, activePositions, ledger } = params;

  const undeployedNotAuthorizedInr = Math.max(
    0,
    Number((config.totalDeclaredCapitalInr - config.authorizedAllocationInr).toFixed(2))
  );

  const deployedCapitalInr = Number(
    activePositions
      .reduce((sum, p) => sum + p.avgEntryPrice * p.totalLots * p.lotSize, 0)
      .toFixed(2)
  );

  const reservedCapitalInr = Number(
    reservations
      .filter((r) => r.status === 'Reserved' || r.status === 'Partially Filled')
      .reduce((sum, r) => sum + r.reservedCapitalInr, 0)
      .toFixed(2)
  );

  // Unrealized profit is NEVER added to deployable cash unless explicitly enabled & tested
  const availableAllocationInr = Math.max(
    0,
    Number((config.authorizedAllocationInr - deployedCapitalInr - reservedCapitalInr).toFixed(2))
  );

  const totalStrategyAllocatedInr = Number(
    strategies.reduce((sum, s) => sum + s.allocatedInr, 0).toFixed(2)
  );

  const unassignedAuthorizedInr = Number(
    (config.authorizedAllocationInr - totalStrategyAllocatedInr).toFixed(2)
  );

  const openPositionRiskInr = Number(
    activePositions
      .reduce((sum, p) => sum + Math.max(0, p.avgEntryPrice - p.stopLoss) * p.totalLots * p.lotSize, 0)
      .toFixed(2)
  );

  const activeReservedRiskInr = reservations
    .filter((r) => r.status === 'Reserved' || r.status === 'Partially Filled')
    .reduce((sum, r) => sum + r.reservedRiskInr, 0);

  const realizedLossTodayInr = Math.max(0, -ledger.realizedPnlInr);
  const remainingDailyRiskInr = Math.max(
    0,
    Number((config.dailyLossLimitInr - realizedLossTodayInr - openPositionRiskInr - activeReservedRiskInr).toFixed(2))
  );

  return {
    totalDeclaredCapitalInr: config.totalDeclaredCapitalInr,
    authorizedAllocationInr: config.authorizedAllocationInr,
    undeployedNotAuthorizedInr,
    deployedCapitalInr,
    reservedCapitalInr,
    availableAllocationInr,
    totalStrategyAllocatedInr,
    unassignedAuthorizedInr,
    openPositionsCount: activePositions.length,
    openPositionRiskInr,
    remainingDailyRiskInr,
  };
}

export function validateAuthorizedAllocationChange(params: {
  config: CapitalDeploymentConfig;
  nextAuthorizedInr: number;
  strategies: StrategyAllocation[];
  reservations: CapitalReservation[];
  activePositions: ActivePosition[];
  ledger: CapitalLedger;
}): { ok: boolean; reason?: string } {
  const { config, nextAuthorizedInr, strategies, reservations, activePositions, ledger } = params;
  if (nextAuthorizedInr < 0) {
    return { ok: false, reason: 'Authorized allocation cannot be negative.' };
  }
  if (nextAuthorizedInr > config.totalDeclaredCapitalInr) {
    return {
      ok: false,
      reason: `Authorized allocation (₹${nextAuthorizedInr.toLocaleString('en-IN')}) cannot exceed Total Declared Capital (₹${config.totalDeclaredCapitalInr.toLocaleString('en-IN')}).`,
    };
  }

  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations,
    activePositions,
    ledger,
  });

  const committedCapital = summary.deployedCapitalInr + summary.reservedCapitalInr;
  if (nextAuthorizedInr < committedCapital) {
    return {
      ok: false,
      reason: `Cannot reduce allocation below committed capital (₹${committedCapital.toLocaleString('en-IN')} deployed/reserved). Reconcile or close open positions first.`,
    };
  }

  return { ok: true };
}

/**
 * PositionSizingEngine: calculates eligible option lots strictly from risk budget,
 * authorized deployment balance, strategy allocation, index cap, and exchange limits.
 */
export function calculateStrategyLotSize(params: {
  opportunity: TradeOpportunity;
  strategy: StrategyAllocation;
  config: CapitalDeploymentConfig;
  summary: CapitalDeploymentSummary;
  activePositions: ActivePosition[];
  limits: RiskLimits;
}): LotSizingBreakdown {
  const { opportunity, strategy, config, summary, activePositions, limits } = params;
  const lotSize = opportunity.contract.lotSize;
  const entry = opportunity.contract.ltp;
  const sl = opportunity.tradePlan.initialStopLoss;
  const spreadPct =
    ((opportunity.contract.ask - opportunity.contract.bid) / Math.max(1, entry)) * 100;

  const oneLotCost = estimateOptionRoundTripCosts({
    entryPremium: entry,
    exitPremium: entry,
    lots: 1,
    lotSize,
    spreadPct,
  });

  const premiumRiskPerLot = Math.abs(entry - sl) * lotSize;
  const riskPerLotInr = Number(
    (premiumRiskPerLot + oneLotCost.totalChargesInr + oneLotCost.estimatedSlippageInr).toFixed(2)
  );
  const capitalPerLotInr = Number(
    (entry * lotSize + oneLotCost.totalChargesInr * 0.5).toFixed(2)
  );

  // Remaining strategy allocation (unused capital in other strategies is never borrowed unless explicit)
  const strategyRemainingInr = Math.max(
    0,
    strategy.allocatedInr - strategy.deployedInr - strategy.reservedInr
  );

  const indexDeployedInr = activePositions
    .filter((p) => p.index === opportunity.index)
    .reduce((sum, p) => sum + p.avgEntryPrice * p.totalLots * p.lotSize, 0);
  const remainingIndexCapInr = Math.max(0, config.maxCapitalPerIndexInr - indexDeployedInr);

  const remainingDailyExposureInr = Math.max(
    0,
    config.maxDailyExposureInr - summary.deployedCapitalInr - summary.reservedCapitalInr
  );
  const deployablePoolInr = Math.min(summary.availableAllocationInr, remainingDailyExposureInr);

  const effectiveTradeCapitalCap = Math.min(
    deployablePoolInr,
    config.maxCapitalPerTradeInr,
    strategy.maxCapitalPerTradeInr,
    strategyRemainingInr
  );

  const effectiveRiskCap = Math.min(
    config.maxRiskPerTradeInr,
    strategy.maxRiskPerTradeInr,
    summary.remainingDailyRiskInr
  );

  const riskBasedLots = riskPerLotInr > 0 ? Math.floor(effectiveRiskCap / riskPerLotInr) : 0;
  const capitalBasedLots =
    capitalPerLotInr > 0 ? Math.floor(deployablePoolInr / capitalPerLotInr) : 0;
  const strategyBasedLots =
    capitalPerLotInr > 0
      ? Math.floor(Math.min(strategyRemainingInr, effectiveTradeCapitalCap) / capitalPerLotInr)
      : 0;
  const indexExposureLots =
    capitalPerLotInr > 0 ? Math.floor(remainingIndexCapInr / capitalPerLotInr) : 0;
  const brokerExchangeLots = EXCHANGE_FREEZE_LOTS[opportunity.index] ?? 20;
  const portfolioRiskLots =
    riskPerLotInr > 0 ? Math.floor(summary.remainingDailyRiskInr / riskPerLotInr) : 0;

  const candidates: { label: string; lots: number }[] = [
    { label: 'Risk Budget Limit', lots: riskBasedLots },
    { label: 'Authorized Capital Limit', lots: capitalBasedLots },
    { label: 'Strategy Allocation Limit', lots: strategyBasedLots },
    { label: 'Index Exposure Cap', lots: indexExposureLots },
    { label: 'Exchange Freeze Limit', lots: brokerExchangeLots },
    { label: 'Portfolio Daily Risk Limit', lots: portfolioRiskLots },
  ];

  const minCandidate = candidates.reduce((min, cur) => (cur.lots < min.lots ? cur : min), candidates[0]);
  const eligibleLots = Math.max(0, minCandidate.lots);

  let rejectedReason: string | undefined;
  if (!strategy.enabled) {
    rejectedReason = `Strategy "${strategy.name}" is disabled.`;
  } else if (limits.killSwitchEngaged) {
    rejectedReason = 'Emergency Stop (Kill Switch) is engaged.';
  } else if (summary.openPositionsCount >= config.maxOpenPositions) {
    rejectedReason = `Max open positions reached (${summary.openPositionsCount}/${config.maxOpenPositions}).`;
  } else if (eligibleLots === 0) {
    rejectedReason = `0 eligible lots under ${minCandidate.label} (Requires ₹${capitalPerLotInr.toLocaleString('en-IN')} capital & ₹${riskPerLotInr.toLocaleString('en-IN')} risk per lot).`;
  }

  return {
    eligibleLots: rejectedReason ? 0 : eligibleLots,
    riskPerLotInr,
    capitalPerLotInr,
    estimatedChargesPerLotInr: oneLotCost.totalChargesInr,
    estimatedSlippagePerLotInr: oneLotCost.estimatedSlippageInr,
    riskBasedLots,
    capitalBasedLots,
    strategyBasedLots,
    indexExposureLots,
    brokerExchangeLots,
    portfolioRiskLots,
    limitingFactor: minCandidate.label,
    rejectedReason,
  };
}

export function checkLiveAutomationPrerequisites(params: {
  config: CapitalDeploymentConfig;
  strategies: StrategyAllocation[];
  brokers: BrokerConnection[];
  limits: RiskLimits;
}): { ready: boolean; missing: string[] } {
  const { config, strategies, brokers, limits } = params;
  const missing: string[] = [];

  const connectedBrokerWithOrderAuth = brokers.some(
    (b) => b.authStatus === 'Connected' && b.orderPlacementPerm && b.twoFactorVerified
  );
  if (!connectedBrokerWithOrderAuth) {
    missing.push('Broker connection with 2FA & Order Placement authorization');
  }
  if (!config.liveAutomationConsent) {
    missing.push('Explicit Live Automation user consent');
  }
  if (config.authorizedAllocationInr <= 0) {
    missing.push('Approved capital allocation > ₹0');
  }
  if (!strategies.some((s) => s.enabled && s.allocatedInr > 0)) {
    missing.push('At least one enabled strategy with allocation');
  }
  if (limits.killSwitchEngaged) {
    missing.push('Emergency Stop must be disengaged');
  }
  if (config.automationPaused) {
    missing.push('Automation must be resumed (not paused)');
  }

  return { ready: missing.length === 0, missing };
}

export function reserveCapitalAtomically(params: {
  idempotencyKey: string;
  strategy: StrategyAllocation;
  opportunity: TradeOpportunity;
  lots: number;
  sizing: LotSizingBreakdown;
  config: CapitalDeploymentConfig;
  strategies: StrategyAllocation[];
  reservations: CapitalReservation[];
  activePositions: ActivePosition[];
  ledger: CapitalLedger;
  limits: RiskLimits;
}): {
  ok: boolean;
  reason?: string;
  reservation?: CapitalReservation;
  updatedStrategies: StrategyAllocation[];
  updatedReservations: CapitalReservation[];
} {
  const {
    idempotencyKey,
    strategy,
    opportunity,
    lots,
    sizing,
    config,
    strategies,
    reservations,
    activePositions,
    ledger,
    limits,
  } = params;

  if (limits.killSwitchEngaged || config.automationPaused) {
    return {
      ok: false,
      reason: limits.killSwitchEngaged
        ? 'Emergency Stop engaged: new reservations blocked.'
        : 'Automation is paused.',
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  if (reservations.some((r) => r.idempotencyKey === idempotencyKey && r.status !== 'Released' && r.status !== 'Rejected')) {
    return {
      ok: false,
      reason: `Duplicate order prevented for idempotency key ${idempotencyKey}.`,
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations,
    activePositions,
    ledger,
  });

  const requiredCapital = Number((sizing.capitalPerLotInr * lots).toFixed(2));
  const requiredRisk = Number((sizing.riskPerLotInr * lots).toFixed(2));

  // Hard Capital Protection Rules
  if (requiredCapital > summary.availableAllocationInr) {
    return {
      ok: false,
      reason: `Required capital (₹${requiredCapital.toLocaleString('en-IN')}) exceeds available authorized allocation (₹${summary.availableAllocationInr.toLocaleString('en-IN')}). Undeployed ₹${summary.undeployedNotAuthorizedInr.toLocaleString('en-IN')} remains locked.`,
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  const nextTotalExposure = summary.deployedCapitalInr + summary.reservedCapitalInr + requiredCapital;
  if (nextTotalExposure > config.maxDailyExposureInr) {
    return {
      ok: false,
      reason: `Total exposure ₹${nextTotalExposure.toLocaleString('en-IN')} exceeds Max Daily Exposure limit (₹${config.maxDailyExposureInr.toLocaleString('en-IN')}).`,
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  const strategyAvailable = strategy.allocatedInr - strategy.deployedInr - strategy.reservedInr;
  if (requiredCapital > strategyAvailable && !strategy.explicitReallocationAllowed) {
    return {
      ok: false,
      reason: `Strategy "${strategy.name}" has only ₹${Math.max(0, strategyAvailable).toLocaleString('en-IN')} available; borrowing unused capital from other strategies is disabled.`,
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  if (requiredRisk > summary.remainingDailyRiskInr) {
    return {
      ok: false,
      reason: `Required risk (₹${requiredRisk.toLocaleString('en-IN')}) exceeds remaining daily risk allowance (₹${summary.remainingDailyRiskInr.toLocaleString('en-IN')}).`,
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  if (lots <= 0) {
    return {
      ok: false,
      reason: sizing.rejectedReason || 'Eligible lot size is 0.',
      updatedStrategies: strategies,
      updatedReservations: reservations,
    };
  }

  const newReservation: CapitalReservation = {
    id: `res-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
    idempotencyKey,
    strategyId: strategy.id,
    tradingSymbol: opportunity.contract.tradingSymbol,
    index: opportunity.index,
    requestedLots: lots,
    filledLots: 0,
    lotSize: opportunity.contract.lotSize,
    reservedCapitalInr: requiredCapital,
    reservedRiskInr: requiredRisk,
    status: 'Reserved',
    createdAt: new Date().toLocaleTimeString('en-IN', { hour12: false }),
  };

  const updatedStrategies = strategies.map((s) =>
    s.id === strategy.id
      ? { ...s, reservedInr: Number((s.reservedInr + requiredCapital).toFixed(2)) }
      : s
  );

  return {
    ok: true,
    reservation: newReservation,
    updatedStrategies,
    updatedReservations: [newReservation, ...reservations],
  };
}

export function reconcilePartialOrFullFill(params: {
  reservationId: string;
  filledLotsNow: number;
  fillPrice: number;
  strategies: StrategyAllocation[];
  reservations: CapitalReservation[];
}): {
  updatedStrategies: StrategyAllocation[];
  updatedReservations: CapitalReservation[];
  deployedDeltaInr: number;
  releasedReservationInr: number;
} {
  const { reservationId, filledLotsNow, fillPrice, strategies, reservations } = params;
  const target = reservations.find((r) => r.id === reservationId);
  if (!target || target.status === 'Filled' || target.status === 'Released' || target.status === 'Rejected') {
    return {
      updatedStrategies: strategies,
      updatedReservations: reservations,
      deployedDeltaInr: 0,
      releasedReservationInr: 0,
    };
  }

  const clampedFilled = Math.min(target.requestedLots, Math.max(0, filledLotsNow));
  const newlyFilledLots = Math.max(0, clampedFilled - target.filledLots);
  const deployedDeltaInr = Number((newlyFilledLots * target.lotSize * fillPrice).toFixed(2));
  const perLotReserved = target.reservedCapitalInr / Math.max(1, target.requestedLots);
  const reservationConsumed = Number((newlyFilledLots * perLotReserved).toFixed(2));
  const remainingReserved = Math.max(0, Number((target.reservedCapitalInr - reservationConsumed).toFixed(2)));
  const remainingRisk = Number(
    ((target.reservedRiskInr / Math.max(1, target.requestedLots)) * (target.requestedLots - clampedFilled)).toFixed(2)
  );
  const nextStatus: CapitalReservation['status'] =
    clampedFilled >= target.requestedLots ? 'Filled' : clampedFilled > 0 ? 'Partially Filled' : 'Reserved';

  const updatedReservations = reservations.map((r) =>
    r.id === reservationId
      ? {
          ...r,
          filledLots: clampedFilled,
          reservedCapitalInr: nextStatus === 'Filled' ? 0 : remainingReserved,
          reservedRiskInr: nextStatus === 'Filled' ? 0 : remainingRisk,
          status: nextStatus,
        }
      : r
  );

  const releasedReservationInr = nextStatus === 'Filled' ? target.reservedCapitalInr : reservationConsumed;

  const updatedStrategies = strategies.map((s) =>
    s.id === target.strategyId
      ? {
          ...s,
          reservedInr: Math.max(0, Number((s.reservedInr - releasedReservationInr).toFixed(2))),
          deployedInr: Number((s.deployedInr + deployedDeltaInr).toFixed(2)),
        }
      : s
  );

  return {
    updatedStrategies,
    updatedReservations,
    deployedDeltaInr,
    releasedReservationInr,
  };
}

export function releaseOrRejectReservation(params: {
  reservationId: string;
  outcome: 'Released' | 'Rejected';
  reason: string;
  strategies: StrategyAllocation[];
  reservations: CapitalReservation[];
}): {
  updatedStrategies: StrategyAllocation[];
  updatedReservations: CapitalReservation[];
  releasedInr: number;
} {
  const { reservationId, outcome, reason, strategies, reservations } = params;
  const target = reservations.find((r) => r.id === reservationId);
  if (!target || target.status === 'Filled' || target.status === 'Released' || target.status === 'Rejected') {
    return {
      updatedStrategies: strategies,
      updatedReservations: reservations,
      releasedInr: 0,
    };
  }

  const releasedInr = target.reservedCapitalInr;

  const updatedReservations = reservations.map((r) =>
    r.id === reservationId
      ? {
          ...r,
          reservedCapitalInr: 0,
          reservedRiskInr: 0,
          status: outcome,
          reason,
        }
      : r
  );

  const updatedStrategies = strategies.map((s) =>
    s.id === target.strategyId
      ? {
          ...s,
          reservedInr: Math.max(0, Number((s.reservedInr - releasedInr).toFixed(2))),
        }
      : s
  );

  return {
    updatedStrategies,
    updatedReservations,
    releasedInr,
  };
}

/**
 * Automated Trade Selection: picks strongest eligible market opportunity for an enabled strategy
 * and verifies contract validity, liquidity, and risk budget.
 */
export function selectBestAutomatedOpportunity(params: {
  opportunities: TradeOpportunity[];
  strategies: StrategyAllocation[];
  indices: Record<IndexSymbol, IndexMasterSpec>;
}): { opportunity: TradeOpportunity | null; strategy: StrategyAllocation | null; rejectionReason?: string } {
  const { opportunities, strategies, indices } = params;

  const sorted = [...opportunities].sort((a, b) => a.suitabilityRank - b.suitabilityRank);

  for (const opp of sorted) {
    if (opp.decision !== 'STRONG OPPORTUNITY' && opp.decision !== 'VALID OPPORTUNITY') continue;
    if (!opp.contract.spreadValid || !opp.probability.sufficientEvidence) continue;
    const spec = indices[opp.index];
    if (!spec || !spec.expiries.includes(opp.contract.expiry)) continue;

    const matchingStrategy = strategies.find(
      (s) => s.enabled && s.allocatedInr > 0 && (s.index === opp.index || s.index === 'ALL')
    );
    if (matchingStrategy) {
      return { opportunity: opp, strategy: matchingStrategy };
    }
  }

  return {
    opportunity: null,
    strategy: null,
    rejectionReason: 'No eligible opportunity matched an enabled strategy with validated probability & liquidity.',
  };
}
