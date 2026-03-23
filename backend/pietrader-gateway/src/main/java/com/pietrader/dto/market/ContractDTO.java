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
public class ContractDTO {

    @JsonProperty("lot_size")
    private Integer lotSize;

    @JsonProperty("tick_size")
    private Double tickSize;

    @JsonProperty("strike_gap")
    private Integer strikeGap;

    @JsonProperty("index_name")
    private String indexName;
}
