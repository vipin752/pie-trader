import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateStrategyLotSize,
  checkLiveAutomationPrerequisites,
  getCapitalDeploymentSummary,
  reconcilePartialOrFullFill,
  releaseOrRejectReservation,
  reserveCapitalAtomically,
  validateAuthorizedAllocationChange,
} from '../src/engine/capitalAllocationEngine.ts';
import {
  INITIAL_BROKERS,
  INITIAL_CAPITAL_LEDGER,
  INITIAL_DEPLOYMENT_CONFIG,
  INITIAL_OPPORTUNITIES,
  INITIAL_RISK_LIMITS,
  INITIAL_STRATEGY_ALLOCATIONS,
} from '../src/data/pieMarketData.ts';

test('1. ₹2L authorization cannot access remaining ₹8L undeployed user capital', () => {
  const config = { ...INITIAL_DEPLOYMENT_CONFIG, totalDeclaredCapitalInr: 1000000, authorizedAllocationInr: 200000 };
  const strategies = INITIAL_STRATEGY_ALLOCATIONS.map((s) => ({ ...s, deployedInr: 0, reservedInr: 0 }));
  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  assert.equal(summary.undeployedNotAuthorizedInr, 800000);
  assert.equal(summary.availableAllocationInr, 200000);

  // Try reserving ₹2,50,000 when only ₹2,00,000 is authorized
  const opp = INITIAL_OPPORTUNITIES[0];
  const oversizedLots = 30;
  const sizing = calculateStrategyLotSize({
    opportunity: opp,
    strategy: strategies[0],
    config,
    summary,
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });
  const res = reserveCapitalAtomically({
    idempotencyKey: 'k-oversize',
    strategy: strategies[0],
    opportunity: opp,
    lots: oversizedLots,
    sizing,
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(res.ok, false);
});

test('2. Lot size is calculated strictly from risk and available capital', () => {
  const config = { ...INITIAL_DEPLOYMENT_CONFIG, maxRiskPerTradeInr: 4000 };
  const strategies = INITIAL_STRATEGY_ALLOCATIONS.map((s) => ({ ...s, deployedInr: 0, reservedInr: 0 }));
  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  const sizing = calculateStrategyLotSize({
    opportunity: INITIAL_OPPORTUNITIES[0],
    strategy: strategies[0],
    config,
    summary,
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });
  assert.ok(sizing.eligibleLots >= 1);
  assert.ok(sizing.eligibleLots <= sizing.riskBasedLots);
  assert.ok(sizing.eligibleLots <= sizing.capitalBasedLots);
  assert.ok(sizing.eligibleLots <= sizing.strategyBasedLots);
});

test('3. Multiple strategies cannot overspend shared or isolated capital', () => {
  const config = {
    ...INITIAL_DEPLOYMENT_CONFIG,
    dailyLossLimitInr: 40000,
    maxDailyExposureInr: 200000,
  };
  const strategies = INITIAL_STRATEGY_ALLOCATIONS.map((s) => ({ ...s, deployedInr: 0, reservedInr: 0 }));
  // Strategy 0 has 80k; try reserving 6 lots (~67k) then another 3 lots (>80k isolated cap)
  const opp = INITIAL_OPPORTUNITIES[0];
  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  const sizing = calculateStrategyLotSize({
    opportunity: opp,
    strategy: strategies[0],
    config,
    summary,
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });
  const res1 = reserveCapitalAtomically({
    idempotencyKey: 'k-strat-1',
    strategy: strategies[0],
    opportunity: opp,
    lots: 6,
    sizing,
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(res1.ok, true);

  const stratUpdated = res1.updatedStrategies[0];
  const res2 = reserveCapitalAtomically({
    idempotencyKey: 'k-strat-2',
    strategy: stratUpdated,
    opportunity: opp,
    lots: 3,
    sizing,
    config,
    strategies: res1.updatedStrategies,
    reservations: res1.updatedReservations,
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(res2.ok, false);

  // Even across different strategies, total reserved/deployed cannot exceed ₹2,00,000 authorized pool
  const bnfOpp = INITIAL_OPPORTUNITIES[1];
  const bnfSizing = calculateStrategyLotSize({
    opportunity: bnfOpp,
    strategy: res1.updatedStrategies[1],
    config,
    summary: getCapitalDeploymentSummary({
      config,
      strategies: res1.updatedStrategies,
      reservations: res1.updatedReservations,
      activePositions: [],
      ledger: INITIAL_CAPITAL_LEDGER,
    }),
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });
  const res3 = reserveCapitalAtomically({
    idempotencyKey: 'k-strat-shared-overflow',
    strategy: { ...res1.updatedStrategies[1], explicitReallocationAllowed: true },
    opportunity: bnfOpp,
    lots: 15, // ~₹1.47L on top of ~₹67k > ₹2L authorized
    sizing: bnfSizing,
    config,
    strategies: res1.updatedStrategies,
    reservations: res1.updatedReservations,
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(res3.ok, false);
});

test('4. Concurrent / duplicate signals cannot reserve the same capital twice', () => {
  const config = { ...INITIAL_DEPLOYMENT_CONFIG };
  const strategies = INITIAL_STRATEGY_ALLOCATIONS.map((s) => ({ ...s, deployedInr: 0, reservedInr: 0 }));
  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  const opp = INITIAL_OPPORTUNITIES[0];
  const sizing = calculateStrategyLotSize({
    opportunity: opp,
    strategy: strategies[0],
    config,
    summary,
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });
  const first = reserveCapitalAtomically({
    idempotencyKey: 'idem-same-signal',
    strategy: strategies[0],
    opportunity: opp,
    lots: 1,
    sizing,
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(first.ok, true);

  const duplicate = reserveCapitalAtomically({
    idempotencyKey: 'idem-same-signal',
    strategy: first.updatedStrategies[0],
    opportunity: opp,
    lots: 1,
    sizing,
    config,
    strategies: first.updatedStrategies,
    reservations: first.updatedReservations,
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(duplicate.ok, false);
});

test('5 & 6. Partial fills reconcile accurately and rejected orders release reservations', () => {
  const config = { ...INITIAL_DEPLOYMENT_CONFIG };
  const strategies = INITIAL_STRATEGY_ALLOCATIONS.map((s) => ({ ...s, deployedInr: 0, reservedInr: 0 }));
  const summary = getCapitalDeploymentSummary({
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  const opp = INITIAL_OPPORTUNITIES[0];
  const sizing = calculateStrategyLotSize({
    opportunity: opp,
    strategy: strategies[0],
    config,
    summary,
    activePositions: [],
    limits: INITIAL_RISK_LIMITS,
  });

  const reserved = reserveCapitalAtomically({
    idempotencyKey: 'k-partial',
    strategy: strategies[0],
    opportunity: opp,
    lots: 2,
    sizing,
    config,
    strategies,
    reservations: [],
    activePositions: [],
    ledger: INITIAL_CAPITAL_LEDGER,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(reserved.ok, true);
  const resId = reserved.reservation!.id;

  const partial = reconcilePartialOrFullFill({
    reservationId: resId,
    filledLotsNow: 1,
    fillPrice: 148.8,
    strategies: reserved.updatedStrategies,
    reservations: reserved.updatedReservations,
  });
  assert.equal(partial.updatedReservations[0].status, 'Partially Filled');
  assert.equal(partial.updatedReservations[0].filledLots, 1);
  assert.ok(partial.deployedDeltaInr > 0);

  const released = releaseOrRejectReservation({
    reservationId: resId,
    outcome: 'Rejected',
    reason: 'Remaining leg rejected by exchange',
    strategies: partial.updatedStrategies,
    reservations: partial.updatedReservations,
  });
  assert.equal(released.updatedReservations[0].status, 'Rejected');
  assert.equal(released.updatedReservations[0].reservedCapitalInr, 0);
  assert.equal(released.updatedStrategies[0].reservedInr, 0);
});

test('7 & 8. Reducing authorized allocation below committed capital is blocked; Emergency stop blocks new orders', () => {
  const config = { ...INITIAL_DEPLOYMENT_CONFIG };
  const activePositions = [
    {
      id: 'p1',
      index: 'NIFTY 50' as const,
      tradingSymbol: 'NIFTY20NOV24350CE',
      optionType: 'CE' as const,
      expiry: '20 Nov',
      strike: 24350,
      lotSize: 75,
      totalLots: 2,
      avgEntryPrice: 150,
      currentLtp: 160,
      stopLoss: 130,
      target1: 185,
      target2: 210,
      trailingMethod: '5m',
      entryStages: [],
      realizedPnlInr: 0,
      mode: 'PAPER' as const,
      openedAt: '10:35 IST',
    },
  ];
  const badReduce = validateAuthorizedAllocationChange({
    config,
    nextAuthorizedInr: 10000, // Less than 22,500 deployed
    strategies: INITIAL_STRATEGY_ALLOCATIONS,
    reservations: [],
    activePositions,
    ledger: INITIAL_CAPITAL_LEDGER,
  });
  assert.equal(badReduce.ok, false);

  const stoppedLimits = { ...INITIAL_RISK_LIMITS, killSwitchEngaged: true };
  const sizingWhenStopped = calculateStrategyLotSize({
    opportunity: INITIAL_OPPORTUNITIES[0],
    strategy: INITIAL_STRATEGY_ALLOCATIONS[0],
    config,
    summary: getCapitalDeploymentSummary({
      config,
      strategies: INITIAL_STRATEGY_ALLOCATIONS,
      reservations: [],
      activePositions: [],
      ledger: INITIAL_CAPITAL_LEDGER,
    }),
    activePositions: [],
    limits: stoppedLimits,
  });
  assert.equal(sizingWhenStopped.eligibleLots, 0);
});

test('9. Live Automated mode never executes without broker order auth and user consent', () => {
  const check = checkLiveAutomationPrerequisites({
    config: { ...INITIAL_DEPLOYMENT_CONFIG, automationMode: 'Live Automated', liveAutomationConsent: false },
    strategies: INITIAL_STRATEGY_ALLOCATIONS,
    brokers: INITIAL_BROKERS,
    limits: INITIAL_RISK_LIMITS,
  });
  assert.equal(check.ready, false);
  assert.ok(check.missing.length >= 2);
});
