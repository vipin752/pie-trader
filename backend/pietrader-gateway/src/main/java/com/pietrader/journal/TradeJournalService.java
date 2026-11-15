package com.pietrader.journal;

import com.pietrader.execution.model.Trade;
import com.pietrader.journal.entity.TradeExecutionEntity;
import com.pietrader.journal.entity.TradeExitEntity;
import com.pietrader.journal.entity.TradeJournalEntity;
import com.pietrader.journal.model.JournalEntry;
import com.pietrader.journal.model.JournalExitEntry;
import com.pietrader.journal.repository.TradeExecutionRepository;
import com.pietrader.journal.repository.TradeExitRepository;
import com.pietrader.journal.repository.TradeJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeJournalService {

    private final TradeJournalRepository   journalRepo;
    private final TradeExecutionRepository executionRepo;
    private final TradeExitRepository      exitRepo;

    @Transactional
    public void saveEntry(JournalEntry e) {
        try {
            TradeJournalEntity entity = new TradeJournalEntity();
            entity.setTradeId(e.getTradeId());
            entity.setSymbol(e.getSymbol());
            entity.setStrike(e.getStrike());
            entity.setDirection(e.getDirection());
            entity.setLots(e.getLots());
            entity.setEntry(e.getEntryPrice());
            entity.setConfidence(e.getConfidence());
            entity.setRegime(e.getRegime());
            entity.setStrategy(e.getStrategy());
            entity.setTradeType(e.getTradeType());
            entity.setMode(e.getMode());
            entity.setGamma(e.getGammaExposure());
            entity.setPcr(e.getPcrValue());
            entity.setFearIndex(e.getFearIndex());
            entity.setSessionPhase(e.getSessionPhase());
            entity.setNotes(e.getDecisionReason());
            entity.setAnalyticsJson(e.getRawDecisionJson());
            // ivContext stored in liquidity column (available column)
            entity.setLiquidity(e.getIvContext());
            entity.setRecordedAt(e.getRecordedAt());
            entity.setIsClosed(false);
            journalRepo.save(entity);
        } catch (Exception ex) {
            log.error("❌ Journal entry failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public void saveExecutionRecord(Trade trade) {
        try {
            TradeExecutionEntity e = new TradeExecutionEntity();
            e.setTradeId(trade.getTradeId());
            e.setOrderId(trade.getOrderId());
            e.setSymbol(trade.getSymbol());
            e.setStrike(trade.getStrike());
            e.setDirection(trade.getDirection());
            e.setLots(trade.getLots());
            e.setQuantity(trade.getQuantity());
            e.setEntryPrice(trade.getEntryPrice());
            e.setSl(trade.getSl());
            e.setTarget(trade.getTarget());
            e.setConfidence(trade.getConfidence());
            e.setRegime(trade.getRegime());
            e.setTradeType(trade.getTradeType());
            e.setMode(trade.getMode() != null ? trade.getMode().name() : "PAPER");
            e.setStrategy(trade.getStrategy());
            e.setStatus(trade.isSuccess()
                ? (trade.getOrderId() != null && trade.getOrderId().startsWith("PAPER") ? "PAPER" : "SUCCESS")
                : "FAILED");
            e.setFailureReason(trade.getFailureReason());
            e.setEntryTime(trade.getEntryTime());
            executionRepo.save(e);
        } catch (Exception ex) {
            log.error("❌ Execution record failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public void saveExit(JournalExitEntry exit) {
        try {
            TradeExitEntity e = new TradeExitEntity();
            e.setSymbol(exit.getSymbol());
            e.setExitPrice(exit.getExitPrice());
            e.setExitReason(exit.getExitReason());
            e.setPnl(exit.getPnl());
            e.setExitAt(exit.getExitAt());
            exitRepo.save(e);

            // Find latest open entry for this symbol, close it by ID
            // (avoids invalid JPQL UPDATE with ORDER BY / LIMIT)
            List<TradeJournalEntity> open =
                journalRepo.findOpenEntriesBySymbol(exit.getSymbol());
            if (!open.isEmpty()) {
                journalRepo.closeEntryById(
                    open.get(0).getId(),
                    exit.getExitPrice(),
                    exit.getExitReason(),
                    exit.getPnl(),
                    exit.getExitAt());
            }
        } catch (Exception ex) {
            log.error("❌ Exit record failed: {}", ex.getMessage(), ex);
        }
    }

    public List<TradeJournalEntity> getJournalForSymbol(String symbol) {
        return journalRepo.findBySymbolOrderByRecordedAtDesc(symbol);
    }
    public List<TradeJournalEntity> getOpenEntries() {
        return journalRepo.findByIsClosedFalseOrderByRecordedAtDesc();
    }
    public List<TradeJournalEntity> getAllClosedForTraining() {
        return journalRepo.findClosedTradesForTraining();
    }
    public double getTodayPnl(String symbol) {
        long since = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        Double pnl = exitRepo.sumPnlSince(symbol, since);
        return pnl != null ? pnl : 0.0;
    }
    public List<TradeExitEntity> getTodayExits() {
        long since = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        return exitRepo.findSince(since);
    }
}
