package com.pietrader.journal;

import com.pietrader.execution.model.Trade;
import com.pietrader.journal.entity.TradeExitEntity;
import com.pietrader.journal.entity.TradeJournalEntity;
import com.pietrader.journal.model.JournalEntry;
import com.pietrader.journal.model.JournalExitEntry;

import java.util.List;

/** Persists journal, execution and exit records. Impl: TradeJournalServiceImpl */
public interface IJournalService {
    void                     saveEntry(JournalEntry entry);
    void                     saveExecutionRecord(Trade trade);
    void                     saveExit(JournalExitEntry exit);
    List<TradeJournalEntity> getJournalForSymbol(String symbol);
    List<TradeJournalEntity> getOpenEntries();
    List<TradeJournalEntity> getAllClosedForTraining();
    double                   getTodayPnl(String symbol);
    List<TradeExitEntity>    getTodayExits();
}
