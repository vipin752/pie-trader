package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.liquidity.LiquidityMapDTO;
import com.pietrader.dto.liquidity.LiquiditySweepDTO;
import com.pietrader.dto.liquidity.SupportResistanceDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps liquidity_map section → TradeSignalEntity.
 *
 * Source: dto.getLiquidityMap()
 * Fields: support, resistance, callWall, putWall, liquiditySweepSignal
 */
@Component
@Slf4j
public class LiquidityMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        LiquidityMapDTO liquidity = dto.getLiquidityMap();
        if (liquidity == null) {
            log.debug("liquidity_map is null — skipping");
            return;
        }

        SupportResistanceDTO sr = liquidity.getSupportResistance();
        if (sr != null) {
            entity.setSupport(sr.getSupport());
            entity.setResistance(sr.getResistance());
        }

        LiquiditySweepDTO sweep = liquidity.getLiquiditySweep();
        if (sweep != null) {
            entity.setCallWall(sweep.getCallWall());
            entity.setPutWall(sweep.getPutWall());
            entity.setLiquiditySweepSignal(sweep.getSignal());
        }
    }
}
