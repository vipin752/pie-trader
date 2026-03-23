package com.pietrader.dto.flow;
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
public class InstitutionalFlowDTO {

    @JsonProperty("smart_money_flow_engine")
    private SmartMoneyFlowDTO smartMoneyFlowEngine;

    @JsonProperty("oi_flow_engine")
    private OiFlowEngineDTO oiFlowEngine;

    @JsonProperty("volume_spike_engine")
    private VolumeSpikeEngineDTO volumeSpikeEngine;
}
