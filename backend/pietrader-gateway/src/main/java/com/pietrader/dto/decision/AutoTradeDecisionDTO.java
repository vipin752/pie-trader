package com.pietrader.dto.decision;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoTradeDecisionDTO {

    @JsonProperty("auto_trade_decision")
    private AutoTradeActionDTO autoTradeAction;

    @JsonProperty("trade_recommendations")
    private TradeRecommendationsDTO tradeRecommendations;
}
