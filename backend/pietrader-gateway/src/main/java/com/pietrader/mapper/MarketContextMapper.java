package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.market.ExpiryDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps market_context section → TradeSignalEntity.
 *
 * Source: dto.getMarketContext()
 * Fields: symbol, spot, atm, expiry, daysToExpiry, phase
 */
@Component
@Slf4j
public class MarketContextMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        MarketContextDTO ctx = dto.getMarketContext();
        if (ctx == null) {
            log.debug("market_context is null — skipping");
            return;
        }

        entity.setSymbol(ctx.getSymbol());
        entity.setSpot(ctx.getSpot());
        entity.setAtm(ctx.getAtm());

        ExpiryDTO expiry = ctx.getExpiry();
        if (expiry != null) {
            entity.setExpiry(expiry.getNearestExpiry());
            entity.setDaysToExpiry(expiry.getDaysToExpiry());
            entity.setPhase(expiry.getPhase());
        }
    }
}
