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
public class BreakoutDTO {

    @JsonProperty("status")
    private String status;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("trigger_price")
    private String triggerPrice;

    @JsonProperty("type")
    private String type;
}
