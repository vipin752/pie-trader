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
public class AutoTradeActionDTO {

    @JsonProperty("strategy")
    private String strategy;

    @JsonProperty("action")
    private String action;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("option")
    private String option;

    @JsonProperty("confidence")
    private String confidence;

    @JsonProperty("reason")
    private String reason;
}
