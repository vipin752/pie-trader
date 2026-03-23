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
public class DealerPositioningDTO {

    @JsonProperty("dealer_inventory_model")
    private DealerInventoryModelDTO dealerInventoryModel;

    @JsonProperty("gex")
    private GexDTO gex;

    @JsonProperty("gamma")
    private GammaDTO gamma;
}
