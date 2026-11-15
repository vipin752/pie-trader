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
public class TradeRecommendationsDTO {

    @JsonProperty("score")
    private Integer score;

    @JsonProperty("confidence_level")
    private String confidenceLevel;

    @JsonProperty("parameter_breakdown")
    private TradeParameterBreakdownDTO parameterBreakdown;

    @JsonProperty("safe_trade")
    private TradeActionDTO safeTrade;

    @JsonProperty("moderate_trade")
    private TradeActionDTO moderateTrade;

    @JsonProperty("aggressive_trade")
    private TradeActionDTO aggressiveTrade;
}
