package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.signal.TradeSignalDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps trade_signal section → TradeSignalEntity.
 *
 * Source: dto.getTradeSignal()
 * Fields: strategy, signalReason, signalConfidence
 */
@Component
@Slf4j
public class TradeSignalSectionMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        TradeSignalDTO signal = dto.getTradeSignal();
        if (signal == null) {
            log.debug("trade_signal is null — skipping");
            return;
        }

        entity.setStrategy(signal.getStrategy());
        entity.setSignalReason(signal.getReason());
        entity.setSignalConfidence(signal.getConfidence());
    }
}
