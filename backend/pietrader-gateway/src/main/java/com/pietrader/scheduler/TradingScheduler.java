package com.pietrader.scheduler;

import com.pietrader.execution.TradeStateMachine;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PIE TRADER — TradingScheduler
 *
 * GAP-7 FIX: WATCHED_SYMBOLS previously contained SENSEX (not a supported
 * symbol) and was missing FINNIFTY + MIDCPNIFTY. EOD reset was silently
 * skipping those symbols, leaving stale Redis state overnight.
 *
 * Now aligned with Python SUPPORTED_SYMBOLS:
 *   ["NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"]
 *
 * Schedules:
 *   EOD Reset    — 15:35 IST: clears daily PnL, locks, trade count,
 *                  max loss breach, cooldown, trade phase
 *   Pre-market   — 09:00 IST: logs state for all symbols before open
 *   Heartbeat    — every 5 min: alive signal
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradingScheduler {

    private final TradeStateManager stateManager;
    private final TradeStateMachine stateMachine;

    // GAP-7 FIX: was List.of("NIFTY", "BANKNIFTY", "SENSEX")
    // Aligned with Python SUPPORTED_SYMBOLS constant
    private static final List<String> WATCHED_SYMBOLS =
            List.of("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY");

    /**
     * EOD Reset: every day at 15:35 IST (09:05 UTC).
     * Clears all daily state so next session starts clean.
     */
    @Scheduled(cron = "0 35 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void endOfDayReset() {
        log.info("♻️ EOD RESET — clearing all daily state for {} symbols", WATCHED_SYMBOLS.size());
        for (String symbol : WATCHED_SYMBOLS) {
            stateManager.resetDailyState(symbol);
            stateMachine.reset(symbol);
            log.info("  ↳ {} reset → NO_TRADE", symbol);
        }
        log.info("♻️ EOD RESET COMPLETE");
    }

    /**
     * Pre-market check: every day at 09:00 IST.
     * Logs state for all symbols so you can confirm clean startup.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void preMarketLog() {
        log.info("🌅 PRE-MARKET CHECK — {} symbols", WATCHED_SYMBOLS.size());
        for (String symbol : WATCHED_SYMBOLS) {
            log.info("  {} → position={} locked={} maxLoss={} phase={} pnl=₹{}",
                    symbol,
                    stateManager.hasActivePosition(symbol),
                    stateManager.isLocked(symbol),
                    stateManager.isMaxLossBreached(symbol),
                    stateMachine.getPhase(symbol),
                    stateManager.getDailyPnl(symbol));
        }
    }

    /**
     * Health heartbeat: every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void heartbeat() {
        log.debug("💓 PIE Trader heartbeat — system alive");
    }
}
