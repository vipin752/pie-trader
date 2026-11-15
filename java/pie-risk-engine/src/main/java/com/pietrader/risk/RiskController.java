package com.pietrader.risk;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
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

    @PostMapping("/api/risk/close")
    public void close(@RequestParam String underlying, @RequestParam double pnl) {
        engine.onClose(underlying, pnl, LocalDateTime.now());
    }
}
