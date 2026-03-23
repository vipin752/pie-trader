package com.pietrader.dto.liquidity;
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
public class OiLadderClustersDTO {

    @JsonProperty("call_clusters")
    private List<OiClusterDTO> callClusters;

    @JsonProperty("put_clusters")
    private List<OiClusterDTO> putClusters;
}
