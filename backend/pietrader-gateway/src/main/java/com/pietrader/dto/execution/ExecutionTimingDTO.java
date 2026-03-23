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
public class ExecutionTimingDTO {

    @JsonProperty("entry_signal")
    private String entrySignal;

    @JsonProperty("entry_type")
    private String entryType;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("confidence")
    private String confidence;
}
