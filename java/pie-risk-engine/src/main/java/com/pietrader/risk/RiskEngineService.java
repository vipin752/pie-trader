package com.pietrader.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic pre-trade approval — the Java equivalent of
 * {@code pie_trader.rules.Guard.check}. Postgres (via the two repositories)
 * is the durable source of truth for the day ledger and the audit trail, so
 * a restart recovers the real state instead of trusting memory (Critical
 * Safety #4/#8). A short-lived Redis lock, scoped to the index's
 * correlation group, stops two replicas from deciding on the same group at
 * once (Critical Safety #5/#7) — it protects the DECISION step only; full
 * open-position tracking across a trade's lifetime needs a Position
 * Management service, which does not exist yet (see qa/ for the honest
 * status of that gap).
 */
@Service
public class RiskEngineService {

    // Mirrors pie_trader.auto.instruments.CORRELATION_GROUPS — keep in sync by hand.
    static final Map<String, String> CORRELATION_GROUPS = Map.of(
        "NIFTY", "india_equity_index", "BANKNIFTY", "india_equity_index",
        "FINNIFTY", "india_equity_index", "MIDCPNIFTY", "india_equity_index",
        "SENSEX", "india_equity_index"
    );

    private final RiskRules rules;
    private final DayLedgerRepository ledgers;
    private final RiskDecisionLogRepository decisions;
    private final CloseEventRepository closeEvents;
    private final PortfolioLock lock;

    public RiskEngineService(RiskRules rules, DayLedgerRepository ledgers,
                             RiskDecisionLogRepository decisions, CloseEventRepository closeEvents,
                             PortfolioLock lock) {
        this.rules = rules;
        this.ledgers = ledgers;
        this.decisions = decisions;
        this.closeEvents = closeEvents;
        this.lock = lock;
    }

    @Transactional
    public RiskDecision approve(TradeCandidate c) {
        String group = CORRELATION_GROUPS.getOrDefault(c.underlying(), c.underlying());
        String token = lock.tryAcquire(group, Duration.ofSeconds(5));
        if (token == null) {
            return audit(c, RiskDecision.reject(
                "Another decision is already in flight for this correlation group (%s). Retry.".formatted(group)));
        }
        try {
            return audit(c, decide(c));
        } finally {
            lock.release(group, token);
        }
    }

    private RiskDecision decide(TradeCandidate c) {
        List<String> why = new ArrayList<>();

        if (c.hasPremiums()) {
            if (c.entryPremium() <= 0) why.add("Entry premium must be positive.");
            if (c.stopPremium() < 0) why.add("Stop premium cannot be negative.");
            if (c.entryPremium() > 0 && c.stopPremium() >= 0 && c.entryPremium() - c.stopPremium() <= 0) {
                why.add("Stop must be below entry for a long option; zero or inverted distance.");
            }
        }
        if (c.qty() % c.lotSize() != 0) {
            why.add("Quantity %d is not a whole multiple of the lot size %d.".formatted(c.qty(), c.lotSize()));
        }
        if (!why.isEmpty()) {
            return new RiskDecision(false, why);  // malformed candidate — never touch the ledger
        }

        DayLedgerEntity ledger = ledgers.findById(new DayLedgerEntity.Key(c.underlying(), c.now().toLocalDate()))
            .orElseGet(() -> new DayLedgerEntity(c.underlying(), c.now().toLocalDate()));

        if (ledger.getRealisedPnl().doubleValue() <= -rules.maxDailyLoss()) {
            why.add("Daily loss limit reached (₹%.0f). Done for today.".formatted(ledger.getRealisedPnl().doubleValue()));
        }
        if (ledger.getConsecutiveLosses() >= rules.maxConsecutiveLosses()) {
            why.add("%d losses in a row. Done for today.".formatted(ledger.getConsecutiveLosses()));
        }
        int lots = c.qty() / c.lotSize();
        if (lots > rules.maxLots()) {
            why.add("Size %d exceeds the cap of %d lots.".formatted(lots, rules.maxLots()));
        }
        if (lots < rules.minLots()) {
            why.add("Size %d is below the minimum position of %d lots — it would not fit the risk budget."
                    .formatted(lots, rules.minLots()));
        }
        if (rules.noExpiryDayEntries() && c.expiry() != null && c.expiry().isEqual(c.now().toLocalDate())) {
            why.add("No new positions in a contract on its expiry day.");
        }
        double risk = c.effectiveRisk();
        if (risk > rules.maxLossPerTrade()) {
            why.add("Risk at the stop is ₹%.0f, above the per-trade limit of ₹%.0f."
                    .formatted(risk, rules.maxLossPerTrade()));
        }
        if (rules.cooldownMinutes() > 0 && ledger.getLastLossAt() != null) {
            long elapsed = Duration.between(ledger.getLastLossAt(), c.now()).toMinutes();
            if (elapsed < rules.cooldownMinutes()) {
                why.add("Cooldown after a loss on %s: %d min left."
                        .formatted(c.underlying(), rules.cooldownMinutes() - elapsed));
            }
        }
        return why.isEmpty() ? RiskDecision.approve() : new RiskDecision(false, why);
    }

    private RiskDecision audit(TradeCandidate c, RiskDecision d) {
        decisions.save(new RiskDecisionLogEntity(c.underlying(), c.symbol(), c.qty(),
            BigDecimal.valueOf(c.effectiveRisk()), d.allowed(), String.join("; ", d.reasons()), c.now()));
        return d;
    }

    /**
     * Idempotent close: {@code eventId} must be the broker fill/event's own
     * id, so a replayed Kafka message (Critical Safety #3) moves the ledger
     * exactly once (Suite N Scenario 7). Returns false if this event was
     * already applied.
     */
    @Transactional
    public boolean onClose(String eventId, String underlying, double pnl, LocalDateTime now) {
        if (closeEvents.existsById(eventId)) {
            return false;  // already applied — classic duplicate-fill protection
        }
        closeEvents.save(new CloseEventEntity(eventId, underlying, BigDecimal.valueOf(pnl), now));

        DayLedgerEntity ledger = ledgers.findById(new DayLedgerEntity.Key(underlying, now.toLocalDate()))
            .orElseGet(() -> new DayLedgerEntity(underlying, now.toLocalDate()));
        ledger.setRealisedPnl(ledger.getRealisedPnl().add(BigDecimal.valueOf(pnl)));
        if (pnl < 0) {
            ledger.setConsecutiveLosses(ledger.getConsecutiveLosses() + 1);
            ledger.setLastLossAt(now);
        } else {
            ledger.setConsecutiveLosses(0);
        }
        ledgers.save(ledger);
        return true;
    }
}
