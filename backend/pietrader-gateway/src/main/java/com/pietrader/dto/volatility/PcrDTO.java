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
public class PcrDTO {

    @JsonProperty("pcr")
    private Double pcr;

    @JsonProperty("put_oi")
    private Long putOi;

    @JsonProperty("call_oi")
    private Long callOi;

    @JsonProperty("sentiment")
    private String sentiment;
}
