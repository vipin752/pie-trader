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
public class SupportResistanceDTO {

    @JsonProperty("support")
    private Integer support;

    @JsonProperty("resistance")
    private Integer resistance;
}
