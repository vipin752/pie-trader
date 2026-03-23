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
public class VolatilityContextDTO {

    @JsonProperty("volatility_engine")
    private VolatilityEngineDTO volatilityEngine;

    @JsonProperty("pcr")
    private PcrDTO pcr;
}
