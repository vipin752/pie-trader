package com.pietrader.service;

import com.pietrader.dto.OptionAnalyticsDTO;

public interface OptionService {

    /**
     * Pure read: fetches the latest analytics JSON from the Python engine
     * and caches it (Redis) for the dashboard/signal endpoints that serve
     * it. Never persists a signal and never triggers execution — safe to
     * call from any GET endpoint, any number of times, including on a
     * cache miss.
     *
     * CR-002 fix: this method used to also call TradeSignalService.process()
     * and ExecutionService.execute() unconditionally, so a plain GET (e.g.
     * the dashboard's own trade-card fallback) could trigger a real order
     * attempt on a cache miss. Execution now only happens through the
     * Kafka AnalyticsConsumer path (ExecutionService.execute()'s own
     * javadoc: "called by Kafka consumer") or through
     * triggerAnalyticsCycle() below, which is an explicit POST command,
     * never a read.
     */
    String getOptionSummary(String symbol);

    /**
     * Command, not a query: fetches, persists the signal, and runs the
     * execution engine for `symbol`. This is the explicit manual-trigger
     * path OptionController's POST endpoint uses — it must never be wired
     * to a GET route. Every order it can lead to still passes through
     * ExecutionGate/RiskManager exactly as the Kafka path does.
     */
    String triggerAnalyticsCycle(String symbol);

    OptionAnalyticsDTO getLatestSignal(String symbol);
}
