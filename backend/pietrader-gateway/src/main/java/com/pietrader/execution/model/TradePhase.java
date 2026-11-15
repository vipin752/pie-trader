package com.pietrader.execution.model;

/**
 * PIE TRADER — TradePhase
 *
 * The 7 states of the trade state machine.
 * Stored in Redis as trade:phase:SYMBOL.
 *
 * Transitions:
 *   NO_TRADE → PREPARE → READY → EXECUTE → MANAGE → EXIT → NO_TRADE
 *
 * NO_TRADE   : No setup. Market ranging, no compression.
 * PREPARE    : Compression detected. Breakout imminent. Watch the levels.
 * READY      : Price within 0.5% of support/resistance. Breakout trigger near.
 * EXECUTE    : Breakout confirmed. Place order NOW.
 * MANAGE     : In trade. SL + trailing active.
 * EXIT       : Exit triggered (SL / target / time / signal reversal).
 */
public enum TradePhase {
    NO_TRADE,
    PREPARE,
    READY,
    EXECUTE,
    MANAGE,
    EXIT;

    /** Human-readable description for trade card UI */
    public String describe() {
	return switch (this) {
	    case NO_TRADE -> "No setup — market ranging";
	    case PREPARE  -> "Compression forming — watch levels";
	    case READY    -> "Level near — breakout trigger imminent";
	    case EXECUTE  -> "Breakout confirmed — place trade";
	    case MANAGE   -> "In trade — manage position";
	    case EXIT     -> "Exit triggered — close position";
	};
    }

    /** True when a trade can or should be placed */
    public boolean isActionable() {
	return this == EXECUTE;
    }

    /** True when we're already in a trade */
    public boolean isActive() {
	return this == MANAGE || this == EXIT;
    }
}
