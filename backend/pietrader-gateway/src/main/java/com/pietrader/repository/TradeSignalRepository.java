package com.pietrader.repository;

import com.pietrader.entity.TradeSignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignalEntity, Long> {

    List<TradeSignalEntity> findBySymbol(String symbol);

    List<TradeSignalEntity> findBySymbolAndAction(String symbol, String action);

    List<TradeSignalEntity> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    Optional<TradeSignalEntity> findTopBySymbolOrderByCreatedAtDesc(String symbol);

    List<TradeSignalEntity> findByDirectionAndDecisionConfidence(String direction, String confidence);
}