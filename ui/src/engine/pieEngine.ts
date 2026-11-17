import {
  ActivePosition,
  BasketLeg,
  CapitalLedger,
  IndexMasterSpec,
  IndexSymbol,
  OptionChainRow,
  RiskLimits,
  RiskProfile,
  TradeOpportunity,
} from '../types/pie';

export const RISK_PROFILE_PRESETS: Record<RiskProfile, Partial<RiskLimits>> = {
  Conservative: {
    profile: 'Conservative',
    maxRiskPerTradePct: 0.5,
    maxRiskPerBasketPct: 1.25,
    dailyLossLimitInr: 12000,
    weeklyLossLimitInr: 30000,
    maxDrawdownPct: 5,
    maxTradesPerDay: 4,
    consecutiveLossLimit: 2,
    maxSpreadPct: 1.8,
    minLiquidityScore: 72,
    maxIndexExposurePct: 18,
  },
  Moderate: {
    profile: 'Moderate',
    maxRiskPerTradePct: 1.0,
    maxRiskPerBasketPct: 2.25,
    dailyLossLimitInr: 22000,
    weeklyLossLimitInr: 55000,
    maxDrawdownPct: 8,
    maxTradesPerDay: 6,
    consecutiveLossLimit: 3,
    maxSpreadPct: 2.2,
    minLiquidityScore: 65,
    maxIndexExposurePct: 25,
  },
  Aggressive: {
    profile: 'Aggressive',
    maxRiskPerTradePct: 1.75,
    maxRiskPerBasketPct: 3.5,
    dailyLossLimitInr: 35000,
    weeklyLossLimitInr: 90000,
    maxDrawdownPct: 12,
    maxTradesPerDay: 8,
    consecutiveLossLimit: 3,
    maxSpreadPct: 2.8,
    minLiquidityScore: 55,
    maxIndexExposurePct: 35,
  },
};

function normalCdf(x: number): number {
  const t = 1 / (1 + 0.2316419 * Math.abs(x));
  const d = 0.3989423 * Math.exp((-x * x) / 2);
  const p =
    d *
    t *
    (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
  return x > 0 ? 1 - p : p;
}

function normalPdf(x: number): number {
  return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
}

export function blackScholesOption(params: {
  spot: number;
  strike: number;
  timeYears: number;
  rate: number;
  ivDecimal: number;
  optionType: 'CE' | 'PE';
}) {
  const { spot, strike, timeYears, rate, ivDecimal, optionType } = params;
  const T = Math.max(timeYears, 1 / 365);
  const sigma = Math.max(ivDecimal, 0.06);
  const sqrtT = Math.sqrt(T);
  const d1 = (Math.log(spot / strike) + (rate + 0.5 * sigma * sigma) * T) / (sigma * sqrtT);
  const d2 = d1 - sigma * sqrtT;

  const price =
    optionType === 'CE'
      ? spot * normalCdf(d1) - strike * Math.exp(-rate * T) * normalCdf(d2)
      : strike * Math.exp(-rate * T) * normalCdf(-d2) - spot * normalCdf(-d1);

  const delta = optionType === 'CE' ? normalCdf(d1) : normalCdf(d1) - 1;
  const gamma = normalPdf(d1) / (spot * sigma * sqrtT);
  const thetaAnnual =
    optionType === 'CE'
      ? -(spot * normalPdf(d1) * sigma) / (2 * sqrtT) - rate * strike * Math.exp(-rate * T) * normalCdf(d2)
      : -(spot * normalPdf(d1) * sigma) / (2 * sqrtT) + rate * strike * Math.exp(-rate * T) * normalCdf(-d2);
  const vega = (spot * normalPdf(d1) * sqrtT) / 100;

  return {
    price: Math.max(0.5, Number(price.toFixed(2))),
    delta: Number(delta.toFixed(3)),
    gamma: Number(gamma.toFixed(5)),
    theta: Number((thetaAnnual / 365).toFixed(2)),
    vega: Number(vega.toFixed(2)),
  };
}

export function generateOptionChain(
  spec: IndexMasterSpec,
  expiryIndex = 0
): OptionChainRow[] {
  const step = spec.strikeInterval;
  const atmStrike = Math.round(spec.spot / step) * step;
  const daysSchedule = [3, 10, 17, 38];
  const daysToExpiry = daysSchedule[expiryIndex] ?? 24 + expiryIndex * 10;
  const timeYears = daysToExpiry / 365;
  const termBaseMultiplier = expiryIndex === 0 ? 1.07 : expiryIndex === 1 ? 1.0 : expiryIndex === 2 ? 0.96 : 0.93;
  const smileSteepness = expiryIndex === 0 ? 1.35 : expiryIndex === 1 ? 1.05 : expiryIndex === 2 ? 0.85 : 0.7;
  const baseIv = Math.max(0.1, (spec.indiaVix / 100) * termBaseMultiplier);
  const expiryCode = (spec.expiries[expiryIndex] ?? spec.expiries[0]).replace(/\s+/g, '').toUpperCase();
  const shortSym = spec.symbol === 'NIFTY 50' ? 'NIFTY' : spec.symbol;

  const rows: OptionChainRow[] = [];

  for (let offset = -6; offset <= 6; offset++) {
    const strike = atmStrike + offset * step;
    const moneyness = (strike - spec.spot) / spec.spot;
    const wingQuadratic = moneyness * moneyness * 14 * smileSteepness;
    const ceIv = Math.max(
      0.085,
      baseIv - moneyness * 0.45 * smileSteepness + Math.abs(moneyness) * 0.28 + wingQuadratic
    );
    const peIv = Math.max(
      0.09,
      baseIv + Math.max(0, -moneyness) * 0.95 * smileSteepness + Math.abs(moneyness) * 0.24 + wingQuadratic
    );

    const ceBs = blackScholesOption({
      spot: spec.spot,
      strike,
      timeYears,
      rate: 0.068,
      ivDecimal: ceIv,
      optionType: 'CE',
    });
    const peBs = blackScholesOption({
      spot: spec.spot,
      strike,
      timeYears,
      rate: 0.068,
      ivDecimal: peIv,
      optionType: 'PE',
    });

    const distanceSteps = Math.abs(offset);
    const ceSpread = Math.max(0.1, Number((ceBs.price * (0.004 + distanceSteps * 0.0018)).toFixed(2)));
    const peSpread = Math.max(0.1, Number((peBs.price * (0.004 + distanceSteps * 0.0018)).toFixed(2)));

    const ceBid = Number(Math.max(0.05, ceBs.price - ceSpread / 2).toFixed(2));
    const ceAsk = Number((ceBs.price + ceSpread / 2).toFixed(2));
    const peBid = Number(Math.max(0.05, peBs.price - peSpread / 2).toFixed(2));
    const peAsk = Number((peBs.price + peSpread / 2).toFixed(2));

    const ceSpreadPct = Number((((ceAsk - ceBid) / ceBs.price) * 100).toFixed(2));
    const peSpreadPct = Number((((peAsk - peBid) / peBs.price) * 100).toFixed(2));

    // Deterministic OI skew reflecting institutional support/resistance around ATM
    const ceOiBase = Math.round(
      (1650000 + (offset >= 0 ? 1400000 * Math.exp(-Math.pow(offset - 2, 2) / 5) : 320000)) *
        (spec.symbol === 'NIFTY 50' ? 1.45 : spec.symbol === 'BANKNIFTY' ? 1.15 : 0.72)
    );
    const peOiBase = Math.round(
      (1720000 + (offset <= 0 ? 1580000 * Math.exp(-Math.pow(offset + 2, 2) / 5) : 340000)) *
        (spec.symbol === 'NIFTY 50' ? 1.45 : spec.symbol === 'BANKNIFTY' ? 1.15 : 0.72)
    );

    const ceChgOi = Math.round(ceOiBase * (offset >= 1 ? 0.14 : -0.08));
    const peChgOi = Math.round(peOiBase * (offset <= 0 ? 0.19 : -0.05));

    const ceLiquidity = Math.max(42, Math.round(97 - distanceSteps * 5.2 - ceSpreadPct * 4));
    const peLiquidity = Math.max(42, Math.round(97 - distanceSteps * 5.2 - peSpreadPct * 4));

    rows.push({
      strike,
      isAtm: offset === 0,
      ce: {
        tradingSymbol: `${shortSym}${expiryCode}${strike}CE`,
        instrumentToken: `${spec.exchange === 'NSE' ? 'NFO' : 'BFO'}-${strike}-CE-${expiryIndex}`,
        ltp: ceBs.price,
        bid: ceBid,
        ask: ceAsk,
        spreadPct: ceSpreadPct,
        oi: ceOiBase,
        chgOi: ceChgOi,
        volume: Math.round(ceOiBase * 2.4),
        iv: Number((ceIv * 100).toFixed(2)),
        delta: ceBs.delta,
        gamma: ceBs.gamma,
        theta: ceBs.theta,
        vega: ceBs.vega,
        liquidityScore: ceLiquidity,
        spreadValid: ceSpreadPct <= 2.0,
      },
      pe: {
        tradingSymbol: `${shortSym}${expiryCode}${strike}PE`,
        instrumentToken: `${spec.exchange === 'NSE' ? 'NFO' : 'BFO'}-${strike}-PE-${expiryIndex}`,
        ltp: peBs.price,
        bid: peBid,
        ask: peAsk,
        spreadPct: peSpreadPct,
        oi: peOiBase,
        chgOi: peChgOi,
        volume: Math.round(peOiBase * 2.2),
        iv: Number((peIv * 100).toFixed(2)),
        delta: peBs.delta,
        gamma: peBs.gamma,
        theta: peBs.theta,
        vega: peBs.vega,
        liquidityScore: peLiquidity,
        spreadValid: peSpreadPct <= 2.0,
      },
    });
  }

  return rows;
}

export interface CostBreakdown {
  grossValueInr: number;
  brokerageInr: number;
  sttInr: number;
  exchangeTxInr: number;
  sebiTurnoverInr: number;
  stampDutyInr: number;
  gstInr: number;
  totalChargesInr: number;
  estimatedSlippageInr: number;
}

/**
 * Deterministic Indian index options transaction cost & statutory tax estimator
 */
export function estimateOptionRoundTripCosts(params: {
  entryPremium: number;
  exitPremium: number;
  lots: number;
  lotSize: number;
  spreadPct?: number;
}): CostBreakdown {
  const qty = Math.max(1, params.lots) * params.lotSize;
  const buyTurnover = params.entryPremium * qty;
  const sellTurnover = params.exitPremium * qty;
  const totalTurnover = buyTurnover + sellTurnover;

  // Flat Rs 20 per executed order (entry + exit = Rs 40)
  const brokerageInr = 40;
  // STT on option sell side ~0.1% of premium turnover
  const sttInr = sellTurnover * 0.001;
  // NSE/BSE option transaction charges ~0.03503%
  const exchangeTxInr = totalTurnover * 0.0003503;
  // SEBI turnover fee Rs 10 per crore
  const sebiTurnoverInr = totalTurnover * 0.000001;
  // Stamp duty on buy side 0.003%
  const stampDutyInr = buyTurnover * 0.00003;
  // 18% GST on brokerage + exchange + SEBI
  const gstInr = (brokerageInr + exchangeTxInr + sebiTurnoverInr) * 0.18;

  const totalChargesInr = Number(
    (brokerageInr + sttInr + exchangeTxInr + sebiTurnoverInr + stampDutyInr + gstInr).toFixed(2)
  );

  const spreadFraction = Math.max(0.0025, (params.spreadPct ?? 0.6) / 100);
  const estimatedSlippageInr = Number((buyTurnover * spreadFraction * 0.5).toFixed(2));

  return {
    grossValueInr: Number((sellTurnover - buyTurnover).toFixed(2)),
    brokerageInr: Number(brokerageInr.toFixed(2)),
    sttInr: Number(sttInr.toFixed(2)),
    exchangeTxInr: Number(exchangeTxInr.toFixed(2)),
    sebiTurnoverInr: Number(sebiTurnoverInr.toFixed(2)),
    stampDutyInr: Number(stampDutyInr.toFixed(2)),
    gstInr: Number(gstInr.toFixed(2)),
    totalChargesInr,
    estimatedSlippageInr,
  };
}

export interface OpportunityFinancials {
  allocatedCapitalInr: number;
  initialRiskInr: number;
  maxTotalRiskInr: number;
  potentialGrossProfitInr: number;
  estimatedFeesAndTaxesInr: number;
  estimatedSlippageInr: number;
  potentialNetProfitInr: number;
  netRiskReward: number;
  returnOnAllocatedPct: number;
  returnOnMaxRiskPct: number;
  maxEligibleLots: number;
}

export function calculateOpportunityFinancials(
  opp: TradeOpportunity,
  ledger: CapitalLedger,
  limits: RiskLimits
): OpportunityFinancials {
  const qtyPerLot = opp.contract.lotSize;
  const entry = opp.contract.ltp;
  const sl = opp.tradePlan.initialStopLoss;
  const t2 = opp.tradePlan.target2;
  const riskPerPoint = Math.max(1, entry - sl);
  const lossPerLot = riskPerPoint * qtyPerLot;

  const netEquity = ledger.availableCapitalInr + ledger.deployedCapitalInr + ledger.reservedCapitalInr;
  const riskBudgetInr = (netEquity * limits.maxRiskPerTradePct) / 100;
  const maxEligibleLots = Math.max(0, Math.floor(riskBudgetInr / lossPerLot));
  const lots = Math.max(1, Math.min(opp.contract.recommendedLots, Math.max(1, maxEligibleLots)));

  const allocatedCapitalInr = Number((entry * lots * qtyPerLot).toFixed(2));
  const initialRiskInr = Number((lossPerLot * lots).toFixed(2));
  const maxTotalRiskInr = Number(((lossPerLot * lots) + 180).toFixed(2));

  const costs = estimateOptionRoundTripCosts({
    entryPremium: entry,
    exitPremium: t2,
    lots,
    lotSize: qtyPerLot,
    spreadPct: ((opp.contract.ask - opp.contract.bid) / Math.max(1, entry)) * 100,
  });

  const potentialGrossProfitInr = costs.grossValueInr;
  const potentialNetProfitInr = Number(
    (potentialGrossProfitInr - costs.totalChargesInr - costs.estimatedSlippageInr).toFixed(2)
  );
  const netRiskReward = Number((potentialNetProfitInr / Math.max(1, maxTotalRiskInr)).toFixed(2));

  return {
    allocatedCapitalInr,
    initialRiskInr,
    maxTotalRiskInr,
    potentialGrossProfitInr,
    estimatedFeesAndTaxesInr: costs.totalChargesInr,
    estimatedSlippageInr: costs.estimatedSlippageInr,
    potentialNetProfitInr,
    netRiskReward,
    returnOnAllocatedPct: Number(((potentialNetProfitInr / Math.max(1, allocatedCapitalInr)) * 100).toFixed(2)),
    returnOnMaxRiskPct: Number(((potentialNetProfitInr / Math.max(1, maxTotalRiskInr)) * 100).toFixed(2)),
    maxEligibleLots,
  };
}

export interface RiskValidationResult {
  approved: boolean;
  reasons: string[];
  totalCapitalRequiredInr: number;
  worstCaseLossInr: number;
  riskBudgetInr: number;
  remainingRiskBudgetInr: number;
  weightedAvgPremium: number;
  totalLots: number;
}

/**
 * Independent RiskManager — Final non-bypassable authority over any Basket or Add-on order.
 */
export function validateBasketWithRiskManager(params: {
  legs: BasketLeg[];
  ledger: CapitalLedger;
  limits: RiskLimits;
  activePositions: ActivePosition[];
}): RiskValidationResult {
  const { legs, ledger, limits, activePositions } = params;
  const reasons: string[] = [];

  const netEquity = ledger.availableCapitalInr + ledger.reservedCapitalInr + ledger.deployedCapitalInr;
  const riskBudgetInr = Number(((netEquity * limits.maxRiskPerBasketPct) / 100).toFixed(2));

  const openPositionsRiskInr = activePositions.reduce((acc, pos) => {
    const perUnitRisk = Math.max(0, pos.avgEntryPrice - pos.stopLoss);
    return acc + perUnitRisk * pos.totalLots * pos.lotSize;
  }, 0);

  const activeLegs = legs.filter((l) => l.fillStatus !== 'Cancelled' && l.fillStatus !== 'Rejected');
  let totalCapitalRequiredInr = 0;
  let worstCaseLossInr = 0;
  let totalLots = 0;
  let weightedNumerator = 0;
  let weightedDenominator = 0;

  for (const leg of activeLegs) {
    const qty = leg.lots * leg.lotSize;
    totalCapitalRequiredInr += leg.limitPrice * qty;
    const perUnitRisk = Math.max(0, leg.limitPrice - leg.stopLoss);
    worstCaseLossInr += perUnitRisk * qty;
    totalLots += leg.lots;
    weightedNumerator += leg.limitPrice * qty;
    weightedDenominator += qty;
  }

  const weightedAvgPremium = weightedDenominator > 0 ? Number((weightedNumerator / weightedDenominator).toFixed(2)) : 0;
  const remainingRiskBudgetInr = Number((riskBudgetInr - openPositionsRiskInr).toFixed(2));

  if (limits.killSwitchEngaged) {
    reasons.push('Emergency Kill Switch is engaged. All order execution is blocked.');
  }
  if (limits.behaviouralCooldownActive) {
    reasons.push('Trader Psychology Cooldown lock is active. Wait for session reset before submitting orders.');
  }
  if (limits.eventRiskLock) {
    reasons.push('High-impact macro event risk lock is active.');
  }
  if (ledger.dailyTradesCount >= limits.maxTradesPerDay) {
    reasons.push(`Daily trade count limit reached (${ledger.dailyTradesCount}/${limits.maxTradesPerDay}).`);
  }
  if (ledger.consecutiveLosses >= limits.consecutiveLossLimit) {
    reasons.push(`Consecutive loss circuit breaker triggered (${ledger.consecutiveLosses}/${limits.consecutiveLossLimit}).`);
  }
  if (Math.min(0, ledger.weeklyPnlInr) <= -limits.weeklyLossLimitInr) {
    reasons.push('Weekly drawdown limit breached.');
  }
  if (totalCapitalRequiredInr > ledger.availableCapitalInr) {
    reasons.push(
      `Insufficient available capital: requires ₹${totalCapitalRequiredInr.toLocaleString('en-IN')} vs ₹${ledger.availableCapitalInr.toLocaleString('en-IN')} available.`
    );
  }
  if (worstCaseLossInr > remainingRiskBudgetInr) {
    reasons.push(
      `Basket worst-case loss (₹${worstCaseLossInr.toLocaleString('en-IN')}) exceeds remaining risk allowance (₹${Math.max(0, remainingRiskBudgetInr).toLocaleString('en-IN')}).`
    );
  }
  if (activeLegs.length === 0) {
    reasons.push('Basket has no active legs.');
  }

  // Hard Rule: Never allow averaging down on a losing active position
  for (const leg of activeLegs) {
    if (leg.kind === 'Add-on 1' || leg.kind === 'Add-on 2') {
      const matchingOpen = activePositions.find((p) => p.tradingSymbol === leg.tradingSymbol);
      if (matchingOpen && matchingOpen.currentLtp < matchingOpen.avgEntryPrice) {
        reasons.push(
          `HARD RULE BLOCK: Cannot add ${leg.kind} to losing position ${leg.tradingSymbol} (LTP ₹${matchingOpen.currentLtp} < Avg ₹${matchingOpen.avgEntryPrice}). Averaging down is prohibited.`
        );
      }
    }
  }

  return {
    approved: reasons.length === 0,
    reasons,
    totalCapitalRequiredInr: Number(totalCapitalRequiredInr.toFixed(2)),
    worstCaseLossInr: Number(worstCaseLossInr.toFixed(2)),
    riskBudgetInr,
    remainingRiskBudgetInr,
    weightedAvgPremium,
    totalLots,
  };
}

export function computeNetEquity(ledger: CapitalLedger, activePositions: ActivePosition[]): {
  unrealizedPnlInr: number;
  netEquityInr: number;
  drawdownPct: number;
  withdrawableCashInr: number;
} {
  const unrealizedPnlInr = activePositions.reduce((acc, p) => {
    return acc + (p.currentLtp - p.avgEntryPrice) * p.totalLots * p.lotSize;
  }, 0);

  const netEquityInr =
    ledger.availableCapitalInr +
    ledger.reservedCapitalInr +
    ledger.deployedCapitalInr +
    unrealizedPnlInr;

  const peak = Math.max(ledger.peakEquityInr, netEquityInr);
  const drawdownPct = peak > 0 ? Math.max(0, ((peak - netEquityInr) / peak) * 100) : 0;

  return {
    unrealizedPnlInr: Number(unrealizedPnlInr.toFixed(2)),
    netEquityInr: Number(netEquityInr.toFixed(2)),
    drawdownPct: Number(drawdownPct.toFixed(2)),
    // Hard rule: never count unrealized profit as withdrawable cash
    withdrawableCashInr: Number(Math.max(0, ledger.availableCapitalInr).toFixed(2)),
  };
}
