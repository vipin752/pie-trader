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
public class OptimizedStrikeMetaDTO {

    @JsonProperty("used_flow_override")
    private Boolean usedFlowOverride;

    @JsonProperty("used_direction_filter")
    private Boolean usedDirectionFilter;
}
