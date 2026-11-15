package com.pietrader.risk;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Spec §10 shape: every response is a deterministic, explainable decision. */
@RestController
public class RiskController {

    private final RiskEngineService engine;

    public RiskController(RiskEngineService engine) {
        this.engine = engine;
    }

    @PostMapping("/api/risk/decision")
    public RiskDecision decide(@Valid @RequestBody TradeCandidate candidate) {
        return engine.approve(candidate);
    }

    /**
     * eventId must be the broker/Kafka event's own id (idempotency —
     * Critical Safety #3). {@code occurredAt} is the EVENT's own time, not
     * when this endpoint happens to be called — a delayed or replayed fill
     * must book against the trading day it actually happened on, never the
     * server's wall clock (see qa/defect-register DEF-004: a fill processed
     * even a few minutes after midnight used to silently land on the wrong
     * day's ledger).
     */
    @PostMapping("/api/risk/close")
    public CloseResult close(@RequestParam String eventId, @RequestParam String underlying,
                             @RequestParam double pnl,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt) {
        boolean applied = engine.onClose(eventId, underlying, pnl, occurredAt);
        return new CloseResult(applied);
    }

    public record CloseResult(boolean applied) {}
}
