package com.pietrader.dto.risk;
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
public class RiskManagementDTO {

    @JsonProperty("position")
    private String position;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("confidence")
    private String confidence;
}
