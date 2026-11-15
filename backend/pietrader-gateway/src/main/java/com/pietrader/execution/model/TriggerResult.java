package com.pietrader.execution.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * PIE TRADER — TriggerResult
 *
 * Immutable result from BreakoutTriggerEngine.evaluate().
 * Carries the new TradePhase, execution decision, and
 * all data needed to build the execution_plan in the trade card.
 */
@Getter
@Builder
@ToString
public class TriggerResult {

    // ── Phase transition ──────────────────────────────────────────────────────
    private final TradePhase phase;
    private final boolean    shouldExecute;       // true only when phase == EXECUTE

    // ── Direction of the trigger ──────────────────────────────────────────────
    private final String triggerDirection;        // LONG | SHORT | NONE
    private final String triggerReason;           // single-line summary

    // ── Execution plan fields (for trade card) ────────────────────────────────
    private final int    longTrigger;             // price level that fires long  (e.g. 23000)
    private final int    shortTrigger;            // price level that fires short (e.g. 22500)
    private final String longOption;              // e.g. "23000 CE"
    private final String shortOption;             // e.g. "22500 PE"
    private final String expectedMove;            // BIG_MOVE | MODERATE_MOVE | SMALL_MOVE
    private final String tradeType;               // BREAKOUT | REVERSAL | MOMENTUM
    private final boolean confirmationNeeded;
    private final double breakoutDistancePct;     // how far spot is from nearest trigger

    // ── Condition audit trail (for transparency) ──────────────────────────────
    private final List<String> passedConditions;
    private final List<String> failedConditions;

    // ── Strike selected for this trigger ─────────────────────────────────────
    private final String selectedStrike;          // the strike to use if EXECUTE
    private final String selectedDirection;       // BUY_CE | BUY_PE | NONE

    // ─────────────────────────────────────────────────────────────────────────
    // Convenience factory for non-trade states
    // ─────────────────────────────────────────────────────────────────────────

    public static TriggerResult waiting(TradePhase phase, String reason,
	    int longTrigger, int shortTrigger, String longOpt, String shortOpt,
	    double distancePct, List<String> passed, List<String> failed) {
	return TriggerResult.builder()
		.phase(phase)
		.shouldExecute(false)
		.triggerDirection("NONE")
		.triggerReason(reason)
		.longTrigger(longTrigger)
		.shortTrigger(shortTrigger)
		.longOption(longOpt)
		.shortOption(shortOpt)
		.expectedMove("BIG_MOVE")
		.tradeType("BREAKOUT")
		.confirmationNeeded(true)
		.breakoutDistancePct(distancePct)
		.passedConditions(passed)
		.failedConditions(failed)
		.selectedStrike("")
		.selectedDirection("NONE")
		.build();
    }
}
