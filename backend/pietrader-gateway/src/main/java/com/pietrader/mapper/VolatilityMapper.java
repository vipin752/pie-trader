package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.volatility.PcrDTO;
import com.pietrader.dto.volatility.VolatilityContextDTO;
import com.pietrader.dto.volatility.VolatilityEngineDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps volatility_context section → TradeSignalEntity.
 *
 * Source: dto.getVolatilityContext()
 * Fields: atmIvPct, dailyMove, weeklyMove,
 *         upper1d, lower1d, pcr, pcrSentiment
 */
@Component
@Slf4j
public class VolatilityMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        VolatilityContextDTO volatility = dto.getVolatilityContext();
        if (volatility == null) {
            log.debug("volatility_context is null — skipping");
            return;
        }

        VolatilityEngineDTO engine = volatility.getVolatilityEngine();
        if (engine != null) {
            entity.setAtmIvPct(engine.getAtmIvPct());
            entity.setDailyMove(engine.getDailyMove());
            entity.setWeeklyMove(engine.getWeeklyMove());
            entity.setUpper1d(engine.getUpper1d());
            entity.setLower1d(engine.getLower1d());
        }

        PcrDTO pcr = volatility.getPcr();
        if (pcr != null) {
            entity.setPcr(pcr.getPcr());
            entity.setPcrSentiment(pcr.getSentiment());
        }
    }
}
