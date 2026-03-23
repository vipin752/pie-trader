package com.pietrader.journal.repository;

import com.pietrader.journal.entity.TradeJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TradeJournalRepository extends JpaRepository<TradeJournalEntity, Long> {

    List<TradeJournalEntity> findBySymbolOrderByRecordedAtDesc(String symbol);

    List<TradeJournalEntity> findByIsClosedFalseOrderByRecordedAtDesc();

    @Query("SELECT j FROM TradeJournalEntity j " +
           "WHERE j.isClosed = true AND j.pnl IS NOT NULL " +
           "ORDER BY j.recordedAt DESC")
    List<TradeJournalEntity> findClosedTradesForTraining();

    /**
     * Find open entries for symbol, newest first.
     * Caller takes first() and closes it — avoids invalid JPQL LIMIT/ORDER BY in UPDATE.
     */
    @Query("SELECT j FROM TradeJournalEntity j " +
           "WHERE j.symbol = :symbol AND j.isClosed = false " +
           "ORDER BY j.recordedAt DESC")
    List<TradeJournalEntity> findOpenEntriesBySymbol(@Param("symbol") String symbol);

    /**
     * Close a specific entry by primary key.
     * JPQL UPDATE by ID is fully supported — no LIMIT or ORDER BY needed.
     */
    @Modifying
    @Transactional
    @Query("UPDATE TradeJournalEntity j " +
           "SET j.exitPrice = :exitPrice, " +
           "    j.exitReason = :exitReason, " +
           "    j.pnl = :pnl, " +
           "    j.exitAt = :exitAt, " +
           "    j.isClosed = true " +
           "WHERE j.id = :id")
    int closeEntryById(@Param("id")         Long   id,
                       @Param("exitPrice")  Double exitPrice,
                       @Param("exitReason") String exitReason,
                       @Param("pnl")        Double pnl,
                       @Param("exitAt")     Long   exitAt);
}
