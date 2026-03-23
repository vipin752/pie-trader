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
public class VolatilityEngineDTO {

    @JsonProperty("atm_iv_pct")
    private Double atmIvPct;

    @JsonProperty("daily_move")
    private Double dailyMove;

    @JsonProperty("weekly_move")
    private Double weeklyMove;

    @JsonProperty("upper_1d")
    private Double upper1d;

    @JsonProperty("lower_1d")
    private Double lower1d;

    @JsonProperty("upper_1w")
    private Double upper1w;

    @JsonProperty("lower_1w")
    private Double lower1w;
}
