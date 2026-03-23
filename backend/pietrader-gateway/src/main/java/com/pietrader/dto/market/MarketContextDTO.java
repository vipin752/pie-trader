package com.pietrader.dto.market;

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
public class MarketContextDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("spot")
    private Double spot;

    @JsonProperty("atm")
    private Integer atm;

    @JsonProperty("contract")
    private ContractDTO contract;

    @JsonProperty("expiry")
    private ExpiryDTO expiry;

    @JsonProperty("session")
    private SessionDTO session;
}
