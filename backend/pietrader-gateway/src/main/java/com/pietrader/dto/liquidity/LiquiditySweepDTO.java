package com.pietrader.dto.liquidity;
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
public class LiquiditySweepDTO {

    @JsonProperty("signal")
    private String signal;

    @JsonProperty("call_wall")
    private Integer callWall;

    @JsonProperty("put_wall")
    private Integer putWall;
}
