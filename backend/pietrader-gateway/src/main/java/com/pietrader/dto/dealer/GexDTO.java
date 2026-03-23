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
public class GexDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("lot_size")
    private Integer lotSize;

    @JsonProperty("total_gex")
    private Double totalGex;

    @JsonProperty("gamma_regime")
    private String gammaRegime;

    @JsonProperty("levels")
    private List<GexLevelDTO> levels;
}
