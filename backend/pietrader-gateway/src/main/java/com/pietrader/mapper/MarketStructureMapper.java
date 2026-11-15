package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.market.CompressionDTO;
import com.pietrader.dto.market.MarketStructureDTO;
import com.pietrader.dto.market.PressureDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps market_structure section → TradeSignalEntity.
 *
 * Source: dto.getMarketStructure()
 * Fields: compressionDetected, breakoutSignal, pressureLabel
 */
@Component
@Slf4j
public class MarketStructureMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        MarketStructureDTO structure = dto.getMarketStructure();
        if (structure == null) {
            log.debug("market_structure is null — skipping");
            return;
        }

        CompressionDTO compression = structure.getCompression();
        if (compression != null) {
            entity.setCompressionDetected(compression.getCompressionDetected());
            entity.setBreakoutSignal(compression.getBreakoutSignal());
        }

        PressureDTO pressure = structure.getPressure();
        if (pressure != null) {
            entity.setPressureLabel(pressure.getLabel());
        }
    }
}
