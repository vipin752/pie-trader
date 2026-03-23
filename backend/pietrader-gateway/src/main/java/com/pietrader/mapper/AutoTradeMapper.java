package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.decision.TradeRecommendationsDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps auto_trade_decision section → TradeSignalEntity.
 *
 * Source: dto.getAutoTradeDecision()
 * Fields: action, direction, optionStrike, decisionConfidence,
 *         decisionReason, recommendationScore, recommendationLevel
 */
@Component
@Slf4j
public class AutoTradeMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        AutoTradeDecisionDTO autoTradeDecision = dto.getAutoTradeDecision();
        if (autoTradeDecision == null) {
            log.debug("auto_trade_decision is null — skipping");
            return;
        }

        // Map auto_trade_decision.auto_trade_decision
        AutoTradeActionDTO action = autoTradeDecision.getAutoTradeAction();
        if (action != null) {
            entity.setAction(action.getAction());
            entity.setDirection(action.getDirection());
            entity.setOptionStrike(action.getOption());
            entity.setDecisionConfidence(action.getConfidence());
            entity.setDecisionReason(action.getReason());
        }

        // Map auto_trade_decision.trade_recommendations
        TradeRecommendationsDTO recommendations = autoTradeDecision.getTradeRecommendations();
        if (recommendations != null) {
            entity.setRecommendationScore(recommendations.getScore());
            entity.setRecommendationLevel(recommendations.getConfidenceLevel());
        }
    }
}
