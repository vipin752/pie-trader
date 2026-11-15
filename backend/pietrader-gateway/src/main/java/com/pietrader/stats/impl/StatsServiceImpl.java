package com.pietrader.stats.impl;

import com.pietrader.journal.entity.TradeJournalEntity;
import com.pietrader.journal.repository.TradeJournalRepository;
import com.pietrader.stats.IStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsServiceImpl implements IStatsService {

    private final TradeJournalRepository journalRepo;

    @Override
    public Map<String, Object> computeStats(String symbol) {
        List<TradeJournalEntity> trades = symbol != null && !symbol.isBlank()
            ? journalRepo.findBySymbolOrderByRecordedAtDesc(symbol)
            : journalRepo.findClosedTradesForTraining();

        List<TradeJournalEntity> closed = trades.stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsClosed()) && t.getPnl() != null)
            .collect(Collectors.toList());

        if (closed.isEmpty())
            return Map.of("symbol", symbol != null ? symbol : "ALL",
                "message", "No closed trades yet", "total", 0);

        List<Double> pnls   = closed.stream().map(TradeJournalEntity::getPnl).collect(Collectors.toList());
        List<Double> wins   = pnls.stream().filter(p -> p > 0).collect(Collectors.toList());
        List<Double> losses = pnls.stream().filter(p -> p < 0).collect(Collectors.toList());

        int    total        = closed.size();
        double winRate      = 100.0 * wins.size() / total;
        double lossRate     = 100.0 - winRate;
        double avgWin       = wins.isEmpty()   ? 0 : wins.stream().mapToDouble(d->d).average().orElse(0);
        double avgLoss      = losses.isEmpty() ? 0 : Math.abs(losses.stream().mapToDouble(d->d).average().orElse(0));
        double rr           = avgLoss > 0 ? avgWin / avgLoss : 0;
        double expectancy   = ((winRate / 100.0) * avgWin) - ((lossRate / 100.0) * avgLoss);
        double totalWins    = wins.stream().mapToDouble(d->d).sum();
        double totalLosses  = Math.abs(losses.stream().mapToDouble(d->d).sum());
        double profitFactor = totalLosses > 0 ? totalWins / totalLosses : totalWins > 0 ? 999.0 : 0;
        double drawdown     = maxDrawdown(pnls);
        double totalPnl     = pnls.stream().mapToDouble(d->d).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("symbol",       symbol != null ? symbol : "ALL");
        stats.put("total",        total);
        stats.put("wins",         wins.size());
        stats.put("losses",       losses.size());
        stats.put("winRate",      r2(winRate));
        stats.put("avgWin",       r2(avgWin));
        stats.put("avgLoss",      r2(avgLoss));
        stats.put("rr",           r2(rr));
        stats.put("expectancy",   r2(expectancy));
        stats.put("profitFactor", r2(profitFactor));
        stats.put("maxDrawdown",  r2(drawdown));
        stats.put("totalPnl",     r2(totalPnl));
        stats.put("bestRegime",   bestByWinRate(closed, TradeJournalEntity::getRegime));
        stats.put("bestStrategy", bestByWinRate(closed, TradeJournalEntity::getStrategy));
        stats.put("bestTime",     bestByWinRate(closed, TradeJournalEntity::getSessionPhase));
        stats.put("bestStrike",   bestByAvgPnl(closed, TradeJournalEntity::getStrike));
        stats.put("edge",         expectancy > 0 ? "POSITIVE" : "NEGATIVE");
        return stats;
    }

    @Override
    public Map<String, Object> todayStats(String symbol) {
        long since = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        List<TradeJournalEntity> today = journalRepo.findBySymbolOrderByRecordedAtDesc(symbol)
            .stream()
            .filter(t -> Boolean.TRUE.equals(t.getIsClosed()) && t.getPnl() != null
                && t.getRecordedAt() != null && t.getRecordedAt() >= since)
            .collect(Collectors.toList());
        double pnl  = today.stream().mapToDouble(t -> t.getPnl() != null ? t.getPnl() : 0).sum();
        long   wins = today.stream().filter(t -> t.getPnl() != null && t.getPnl() > 0).count();
        return Map.of("symbol", symbol, "trades", today.size(), "wins", wins,
            "losses", today.size() - wins, "totalPnl", r2(pnl),
            "winRate", today.isEmpty() ? 0.0 : r2(100.0 * wins / today.size()));
    }

    @Override
    public void onTradeClosed(String symbol, double pnl, String exitReason) {
        log.info("📈 Stats.onTradeClosed → {} pnl=₹{} reason={}", symbol, r2(pnl), exitReason);
    }

    private double maxDrawdown(List<Double> pnls) {
        double peak = 0, running = 0, maxDd = 0;
        for (double p : pnls) { running += p; if (running > peak) peak = running; double dd = peak - running; if (dd > maxDd) maxDd = dd; }
        return maxDd;
    }
    private String bestByWinRate(List<TradeJournalEntity> trades, java.util.function.Function<TradeJournalEntity, String> fn) {
        Map<String, long[]> m = new HashMap<>();
        for (TradeJournalEntity t : trades) { String k = fn.apply(t); if (k == null || k.isBlank()) continue; m.computeIfAbsent(k, x -> new long[]{0,0}); m.get(k)[1]++; if (t.getPnl() != null && t.getPnl() > 0) m.get(k)[0]++; }
        return m.entrySet().stream().filter(e -> e.getValue()[1] >= 2).max(Comparator.comparingDouble(e -> (double)e.getValue()[0]/e.getValue()[1])).map(Map.Entry::getKey).orElse("INSUFFICIENT_DATA");
    }
    private String bestByAvgPnl(List<TradeJournalEntity> trades, java.util.function.Function<TradeJournalEntity, String> fn) {
        Map<String, List<Double>> m = new HashMap<>();
        for (TradeJournalEntity t : trades) { String k = fn.apply(t); if (k == null || k.isBlank() || t.getPnl() == null) continue; m.computeIfAbsent(k, x -> new ArrayList<>()).add(t.getPnl()); }
        return m.entrySet().stream().filter(e -> e.getValue().size() >= 2).max(Comparator.comparingDouble(e -> e.getValue().stream().mapToDouble(d->d).average().orElse(0))).map(Map.Entry::getKey).orElse("INSUFFICIENT_DATA");
    }
    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
