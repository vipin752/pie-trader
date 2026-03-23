package com.pietrader.dto.flow;
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
public class SmartMoneyFlowDTO {

    @JsonProperty("smart_money_bias")
    private String smartMoneyBias;

    @JsonProperty("atm_flow")
    private String atmFlow;

    @JsonProperty("unusual_flows")
    private List<UnusualFlowDTO> unusualFlows;
}
