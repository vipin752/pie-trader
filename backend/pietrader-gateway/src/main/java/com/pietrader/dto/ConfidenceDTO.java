package com.pietrader.dto;
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
public class ConfidenceDTO {

    @JsonProperty("confidence_score")
    private Integer confidenceScore;

    @JsonProperty("confidence_level")
    private String confidenceLevel;

    @JsonProperty("breakdown")
    private ConfidenceBreakdownDTO breakdown;
}
