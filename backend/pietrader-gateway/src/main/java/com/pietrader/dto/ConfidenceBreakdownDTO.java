package com.pietrader.dto;
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
public class ConfidenceBreakdownDTO {

    @JsonProperty("premium")
    private Integer premium;

    @JsonProperty("gamma")
    private Integer gamma;

    @JsonProperty("compression")
    private Integer compression;

    @JsonProperty("flip_distance")
    private Integer flipDistance;

    @JsonProperty("window")
    private Integer window;

    @JsonProperty("fake_breakout")
    private Integer fakeBreakout;
}
