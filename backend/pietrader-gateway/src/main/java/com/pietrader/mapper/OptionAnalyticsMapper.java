package com.pietrader.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Main orchestrator mapper.
 * Delegates each section to its dedicated sub-mapper
 * and assembles the final TradeSignalEntity.
 *
 * OptionAnalyticsDTO
 *        ↓
 *   [MarketContextMapper]
 *   [TradeSignalMapper]
 *   [AutoTradeMapper]
 *   [MarketStructureMapper]
 *   [VolatilityMapper]
 *   [DealerMapper]
 *   [LiquidityMapper]
 *   [FearIndexMapper]
 *   [ExecutionMapper]
 *        ↓
 *   TradeSignalEntity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OptionAnalyticsMapper {

    private final ObjectMapper objectMapper;
    private final MarketContextMapper marketContextMapper;
    private final TradeSignalSectionMapper tradeSignalSectionMapper;
    private final AutoTradeMapper autoTradeMapper;
    private final MarketStructureMapper marketStructureMapper;
    private final VolatilityMapper volatilityMapper;
    private final DealerMapper dealerMapper;
    private final LiquidityMapper liquidityMapper;
    private final FearIndexMapper fearIndexMapper;
    private final ExecutionMapper executionMapper;

    public TradeSignalEntity toEntity(OptionAnalyticsDTO dto) {
        if (dto == null) {
            log.warn("Received null OptionAnalyticsDTO — skipping mapping");
            return null;
        }

        TradeSignalEntity entity = new TradeSignalEntity();

        marketContextMapper.map(dto, entity);
        tradeSignalSectionMapper.map(dto, entity);
        autoTradeMapper.map(dto, entity);
        marketStructureMapper.map(dto, entity);
        volatilityMapper.map(dto, entity);
        dealerMapper.map(dto, entity);
        liquidityMapper.map(dto, entity);
        fearIndexMapper.map(dto, entity);
        executionMapper.map(dto, entity);

        // Store full raw JSON for audit / replay
        try {
            entity.setRawPayload(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize raw payload: {}", e.getMessage());
        }

        return entity;
    }
}
