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
public class PositionManagementDTO {

    @JsonProperty("position_status")
    private String positionStatus;

    @JsonProperty("state")
    private String state;

    @JsonProperty("plan")
    private PositionPlanDTO plan;

    @JsonProperty("confidence")
    private String confidence;
}
