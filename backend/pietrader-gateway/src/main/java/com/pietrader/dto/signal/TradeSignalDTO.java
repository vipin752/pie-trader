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
public class TradeSignalDTO {

    @JsonProperty("strategy")
    private String strategy;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("confidence")
    private String confidence;
}
