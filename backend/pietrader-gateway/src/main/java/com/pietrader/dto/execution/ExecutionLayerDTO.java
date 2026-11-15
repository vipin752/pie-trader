package com.pietrader.dto.execution;
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
public class ExecutionLayerDTO {

    @JsonProperty("breakout")
    private BreakoutDTO breakout;

    @JsonProperty("confirmation")
    private ConfirmationDTO confirmation;

    @JsonProperty("final_execution")
    private FinalExecutionDTO finalExecution;
}
