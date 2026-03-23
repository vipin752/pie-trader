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
public class FearIndexComponentsDTO {

    @JsonProperty("pcr")
    private FearIndexValueDTO pcr;

    @JsonProperty("iv")
    private FearIndexValueDTO iv;

    @JsonProperty("gamma")
    private FearIndexValueDTO gamma;

    @JsonProperty("volume")
    private FearIndexValueDTO volume;

    @JsonProperty("skew")
    private FearIndexValueDTO skew;
}
