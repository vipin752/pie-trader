package com.pietrader.dto.signal;
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
public class MultiplierProbabilityDTO {

    @JsonProperty("10x")
    private Double tenX;

    @JsonProperty("20x")
    private Double twentyX;

    @JsonProperty("50x")
    private Double fiftyX;

    @JsonProperty("100x")
    private Double hundredX;
}
