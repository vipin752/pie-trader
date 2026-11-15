package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.dealer.GammaDTO;
import com.pietrader.dto.dealer.GexDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps dealer_positioning section → TradeSignalEntity.
 *
 * Source: dto.getDealerPositioning()
 * Fields: dealerInventory, hedgingBehavior, gammaFlip,
 *         netGamma, gammaRegime, callGammaWall, putGammaWall
 */
@Component
@Slf4j
public class DealerMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        DealerPositioningDTO dealer = dto.getDealerPositioning();
        if (dealer == null) {
            log.debug("dealer_positioning is null — skipping");
            return;
        }

        DealerInventoryModelDTO inventory = dealer.getDealerInventoryModel();
        if (inventory != null) {
            entity.setDealerInventory(inventory.getDealerInventory());
            entity.setHedgingBehavior(inventory.getHedgingBehavior());
            entity.setGammaFlip(inventory.getGammaFlip());
            entity.setNetGamma(inventory.getNetGamma());
        }

        GexDTO gex = dealer.getGex();
        if (gex != null) {
            entity.setGammaRegime(gex.getGammaRegime());
        }

        GammaDTO gamma = dealer.getGamma();
        if (gamma != null) {
            entity.setCallGammaWall(gamma.getCallGammaWall());
            entity.setPutGammaWall(gamma.getPutGammaWall());
        }
    }
}
