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
public class CompleteDecisionDTO {

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("spot")
    private Double spot;

    @JsonProperty("fear_index_analysis")
    private FearIndexAnalysisDTO fearIndexAnalysis;

    @JsonProperty("session_classification")
    private SessionClassificationDTO sessionClassification;

    @JsonProperty("probability_matrix")
    private ProbabilityMatrixDTO probabilityMatrix;

    @JsonProperty("btst_analysis")
    private BtstAnalysisDTO btstAnalysis;

    @JsonProperty("trading_card")
    private TradingCardDTO tradingCard;
}
