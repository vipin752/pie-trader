package com.pietrader.dto.dealer;

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
public class DealerInventoryModelDTO {

    @JsonProperty("dealer_inventory")
    private String dealerInventory;

    @JsonProperty("hedging_behavior")
    private String hedgingBehavior;

    @JsonProperty("gamma_squeeze_risk")
    private String gammaSqueeze;

    @JsonProperty("volatility_regime")
    private String volatilityRegime;

    // ✅ Double — JSON sends 23150.0
    @JsonProperty("gamma_flip")
    private Double gammaFlip;

    @JsonProperty("net_gamma")
    private Double netGamma;
}
