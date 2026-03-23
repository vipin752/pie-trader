package com.pietrader.dto.risk;
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
public class PositionPlanDTO {

    @JsonProperty("instruction")
    private String instruction;

    @JsonProperty("next_trigger")
    private String nextTrigger;
}
