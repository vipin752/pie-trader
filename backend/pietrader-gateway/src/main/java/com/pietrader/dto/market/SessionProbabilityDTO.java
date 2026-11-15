package com.pietrader.dto.market;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pietrader.dto.signal.MultiplierProbabilityDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionProbabilityDTO {

    @JsonProperty("probabilities")
    private MultiplierProbabilityDTO probabilities;

    @JsonProperty("best_strike_10x")
    private String bestStrike10x;
}
