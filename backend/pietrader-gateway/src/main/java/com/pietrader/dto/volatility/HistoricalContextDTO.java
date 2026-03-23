package com.pietrader.dto.volatility;
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
public class HistoricalContextDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("avg_pcr")
    private Double avgPcr;

    @JsonProperty("regime")
    private String regime;

    @JsonProperty("samples")
    private Integer samples;
}
