package com.pietrader.dto.signal;
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
public class OptimizedStrikeDTO {

    @JsonProperty("base_strike")
    private String baseStrike;

    @JsonProperty("final_strike")
    private String finalStrike;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("override_type")
    private String overrideType;

    @JsonProperty("probability")
    private MultiplierProbabilityDTO probability;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("meta")
    private OptimizedStrikeMetaDTO meta;
}
