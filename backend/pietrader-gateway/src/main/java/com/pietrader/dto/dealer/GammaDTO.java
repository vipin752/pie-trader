package com.pietrader.dto.dealer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GammaDTO {

    // ✅ Double — JSON sends 23150.0
    @JsonProperty("gamma_flip")
    private Double gammaFlip;

    @JsonProperty("call_gamma_wall")
    private Integer callGammaWall;

    @JsonProperty("put_gamma_wall")
    private Integer putGammaWall;

    @JsonProperty("net_gamma")
    private Double netGamma;

    @JsonProperty("strikes_enriched")
    private List<StrikeEnrichedDTO> strikesEnriched;
}
