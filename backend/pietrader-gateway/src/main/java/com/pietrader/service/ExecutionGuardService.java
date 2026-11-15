package com.pietrader.service;

import com.pietrader.dto.OptionAnalyticsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionGuardService {

    private final TradeExecutionStateService stateService;

    public boolean canExecute(OptionAnalyticsDTO dto) {
	if (dto == null) return false;

	// ── Resolve symbol — safe fallback chain ──────────────────────────────
	// complete_decision may be null if Python pipeline short-circuits
	String symbol = null;
	if (dto.getCompleteDecision() != null) {
	    symbol = dto.getCompleteDecision().getSymbol();
	}
	if (symbol == null && dto.getMarketContext() != null) {
	    symbol = dto.getMarketContext().getSymbol();
	}
	if (symbol == null) {
	    log.warn("⛔ ExecutionGuard: cannot resolve symbol — skipping");
	    return false;
	}

	// ── Resolve action ────────────────────────────────────────────────────
	String action = null;
	try {
	    if (dto.getAutoTradeDecision() != null
		    && dto.getAutoTradeDecision().getAutoTradeAction() != null) {
		action = dto.getAutoTradeDecision().getAutoTradeAction().getAction();
	    }
	} catch (Exception e) {
	    log.warn("⛔ ExecutionGuard: error resolving action: {}", e.getMessage());
	}

	if (!"EXECUTE".equalsIgnoreCase(action)) {
	    log.debug("⛔ ExecutionGuard: action={} (need EXECUTE)", action);
	    return false;
	}

	if (stateService.hasActivePosition(symbol)) {
	    log.debug("⛔ ExecutionGuard: active position exists for {}", symbol);
	    return false;
	}

	if (stateService.isTradeLocked(symbol)) {
	    log.debug("⛔ ExecutionGuard: trade locked for {}", symbol);
	    return false;
	}

	if (stateService.isCooldownActive(symbol)) {
	    log.debug("⛔ ExecutionGuard: cooldown active for {}", symbol);
	    return false;
	}

	if (!stateService.isRiskAllowed()) {
	    log.warn("⛔ ExecutionGuard: risk not allowed");
	    return false;
	}

	return true;
    }
}
