package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.entity.TradeSignalEntity;

/**
 * Base contract for all section mappers.
 * Each mapper is responsible for one section of OptionAnalyticsDTO.
 */
public interface SectionMapper {
    void map(OptionAnalyticsDTO dto, TradeSignalEntity entity);
}
