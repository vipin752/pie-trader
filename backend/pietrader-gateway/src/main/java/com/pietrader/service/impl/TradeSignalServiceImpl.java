package com.pietrader.service.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.entity.TradeSignalEntity;
import com.pietrader.mapper.TradeSignalMapper;
import com.pietrader.repository.TradeSignalRepository;
import com.pietrader.service.TradeSignalService;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * PIE TRADER — TradeSignalServiceImpl
 *
 * Responsibility: SAVE analytics result to DB + cache in Redis.
 * Nothing else.
 *
 * Execution logic lives in TradingOrchestrator (@Primary ExecutionService).
 * This service must NOT lock trades or create positions — that causes
 * a race condition with TradingOrchestrator which also runs from AnalyticsConsumer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeSignalServiceImpl implements TradeSignalService {

    private final TradeSignalRepository repo;
    private final TradeSignalMapper     mapper;
    private final TradeStateManager     stateManager;

    @Override
    public void process(OptionAnalyticsDTO dto) {
        if (dto == null) return;

        try {
            // Map DTO → entity
            TradeSignalEntity entity = mapper.toEntity(dto);
            if (entity == null) {
                log.warn("⚠️ TradeSignalMapper returned null for DTO");
                return;
            }

            // Save to DB
            TradeSignalEntity saved = repo.save(entity);

            // Cache latest signal in Redis for UI
            if (saved.getSymbol() != null && saved.getRawPayload() != null) {
                stateManager.cacheSignal(saved.getSymbol(), saved.getRawPayload());
            }

            log.info("✅ Signal saved → id={} symbol={} action={} confidence={}",
                    saved.getId(), saved.getSymbol(),
                    saved.getAction(), saved.getDecisionConfidence());

        } catch (Exception e) {
            log.error("❌ TradeSignal save failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<TradeSignalEntity> getLatest(String symbol) {
        return repo.findTopBySymbolOrderByCreatedAtDesc(symbol);
    }

    @Override
    public List<TradeSignalEntity> getHistory(String symbol) {
        return repo.findBySymbol(symbol);
    }
}
