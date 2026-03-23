package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.CompleteDecisionDTO;
import com.pietrader.dto.decision.FearIndexAnalysisDTO;
import com.pietrader.dto.decision.FearIndexComponentsDTO;
import com.pietrader.dto.decision.TradingCardDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps complete_decision.fear_index_analysis section → TradeSignalEntity.
 *
 * Source: dto.getCompleteDecision()
 * Fields: fearIndex, fearZone, recommendedAction,
 *         fearPcr, fearIv, fearGamma, fearVolume, fearSkew,
 *         signalTimestamp, session1Card, session3Card, btst
 */
@Component
@Slf4j
public class FearIndexMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        CompleteDecisionDTO decision = dto.getCompleteDecision();
        if (decision == null) {
            log.debug("complete_decision is null — skipping");
            return;
        }

        entity.setSignalTimestamp(decision.getTimestamp());

        FearIndexAnalysisDTO fearAnalysis = decision.getFearIndexAnalysis();
        if (fearAnalysis != null) {
            entity.setFearIndex(fearAnalysis.getCurrentFearIndex());
            entity.setFearZone(fearAnalysis.getZone());
            entity.setRecommendedAction(fearAnalysis.getRecommendedAction());

            FearIndexComponentsDTO components = fearAnalysis.getComponents();
            if (components != null) {
                entity.setFearPcr(components.getPcr() != null ? components.getPcr().getValue() : null);
                entity.setFearIv(components.getIv() != null ? components.getIv().getValue() : null);
                entity.setFearGamma(components.getGamma() != null ? components.getGamma().getValue() : null);
                entity.setFearVolume(components.getVolume() != null ? components.getVolume().getValue() : null);
                entity.setFearSkew(components.getSkew() != null ? components.getSkew().getValue() : null);
            }
        }

        TradingCardDTO tradingCard = decision.getTradingCard();
        if (tradingCard != null) {
            entity.setSession1Card(tradingCard.getSession1());
            entity.setSession3Card(tradingCard.getSession3());
            entity.setBtst(tradingCard.getBtst());
        }
    }
}
