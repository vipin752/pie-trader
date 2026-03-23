package com.pietrader.journal.repository;

import com.pietrader.journal.entity.TradeExitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeExitRepository extends JpaRepository<TradeExitEntity, Long> {
    List<TradeExitEntity> findBySymbolOrderByExitAtDesc(String symbol);

    @Query("SELECT e FROM TradeExitEntity e WHERE e.exitAt >= :since ORDER BY e.exitAt DESC")
    List<TradeExitEntity> findSince(@Param("since") Long since);

    @Query("SELECT SUM(e.pnl) FROM TradeExitEntity e WHERE e.symbol = :symbol AND e.exitAt >= :since")
    Double sumPnlSince(@Param("symbol") String symbol, @Param("since") Long since);
}
