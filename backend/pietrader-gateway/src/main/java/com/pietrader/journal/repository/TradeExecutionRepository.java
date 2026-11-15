package com.pietrader.journal.repository;

import com.pietrader.journal.entity.TradeExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeExecutionRepository extends JpaRepository<TradeExecutionEntity, Long> {
    Optional<TradeExecutionEntity> findByOrderId(String orderId);
    Optional<TradeExecutionEntity> findByTradeId(String tradeId);
    List<TradeExecutionEntity>     findBySymbolOrderByEntryTimeDesc(String symbol);
}
