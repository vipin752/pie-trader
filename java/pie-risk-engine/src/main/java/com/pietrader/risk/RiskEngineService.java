package com.pietrader.risk;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Deterministic pre-trade approval — the Java equivalent of
 * {@code pie_trader.rules.Guard.check}. Every rule here is a straight port;
 * if you change one side, change the other (see the module README on
 * keeping rules.toml and {@link RiskRules#defaults()} in sync by hand).
 */
@Service
public class RiskEngineService {

    private final RiskRules rules;
    private final ConcurrentHashMap<String, DayLedger> ledgers = new ConcurrentHashMap<>();

    public RiskEngineService() {
        this(RiskRules.defaults());
    }

    public RiskEngineService(RiskRules rules) {
        this.rules = rules;
    }

    public RiskDecision approve(TradeCandidate c) {
        DayLedger ledger = ledgers.computeIfAbsent(c.underlying(), k -> new DayLedger());
        ledger.rollIfNewDay(c.now().toLocalDate());

        List<String> why = new ArrayList<>();

        if (ledger.realised <= -rules.maxDailyLoss()) {
            why.add("Daily loss limit reached (₹%.0f). Done for today.".formatted(ledger.realised));
        }
        if (ledger.consecutiveLosses >= rules.maxConsecutiveLosses()) {
            why.add("%d losses in a row. Done for today.".formatted(ledger.consecutiveLosses));
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
        if (c.riskInr() > rules.maxLossPerTrade()) {
            why.add("Risk at the stop is ₹%.0f, above the per-trade limit of ₹%.0f."
                    .formatted(c.riskInr(), rules.maxLossPerTrade()));
        }
        if (rules.cooldownMinutes() > 0) {
            LocalDateTime last = ledger.lastLossAt.get(c.underlying());
            if (last != null) {
                long elapsed = Duration.between(last, c.now()).toMinutes();
                if (elapsed < rules.cooldownMinutes()) {
                    why.add("Cooldown after a loss on %s: %d min left."
                            .formatted(c.underlying(), rules.cooldownMinutes() - elapsed));
                }
            }
        }

        return why.isEmpty() ? RiskDecision.approve() : new RiskDecision(false, why);
    }

    /** Record a closed position's realised P&L against the day's ledger. */
    public void onClose(String underlying, double pnl, LocalDateTime now) {
        DayLedger ledger = ledgers.computeIfAbsent(underlying, k -> new DayLedger());
        ledger.rollIfNewDay(now.toLocalDate());
        ledger.realised += pnl;
        if (pnl < 0) {
            ledger.consecutiveLosses++;
            ledger.lastLossAt.put(underlying, now);
        } else {
            ledger.consecutiveLosses = 0;
        }
    }
}
