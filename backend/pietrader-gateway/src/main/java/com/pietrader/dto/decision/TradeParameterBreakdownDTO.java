package com.pietrader.dto.decision;
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
public class TradeParameterBreakdownDTO {

    @JsonProperty("gamma")
    private Integer gamma;

    @JsonProperty("window")
    private Integer window;

    @JsonProperty("premium")
    private Integer premium;

    @JsonProperty("volume")
    private Integer volume;

    @JsonProperty("compression")
    private Integer compression;

    @JsonProperty("probability")
    private Integer probability;

    @JsonProperty("confidence")
    private Integer confidence;
}
