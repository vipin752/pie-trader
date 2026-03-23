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
public class FinalExecutionDTO {

    @JsonProperty("execution_ready")
    private Boolean executionReady;

    // JSON: "reason":"Market closed" — used in Gate 2 block logging
    @JsonProperty("reason")
    private String reason;
}
