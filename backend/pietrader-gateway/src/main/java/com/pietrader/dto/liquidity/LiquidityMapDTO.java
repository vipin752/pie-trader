package com.pietrader.dto.liquidity;
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
public class LiquidityMapDTO {

    @JsonProperty("support_resistance")
    private SupportResistanceDTO supportResistance;

    @JsonProperty("oi_ladder_clusters")
    private OiLadderClustersDTO oiLadderClusters;

    @JsonProperty("liquidity_sweep")
    private LiquiditySweepDTO liquiditySweep;
}
