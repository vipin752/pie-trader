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
public class ExitManagementDTO {

    @JsonProperty("exit_plan")
    private Object exitPlan;

    @JsonProperty("position_state")
    private String positionState;

    @JsonProperty("reason")
    private String reason;
}
