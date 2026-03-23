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
public class FearIndexAnalysisDTO {

    @JsonProperty("current_fear_index")
    private Double currentFearIndex;

    @JsonProperty("zone")
    private String zone;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("components")
    private FearIndexComponentsDTO components;
}
