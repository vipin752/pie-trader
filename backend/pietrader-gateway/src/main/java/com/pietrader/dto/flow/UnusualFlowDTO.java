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
public class UnusualFlowDTO {

    @JsonProperty("strike")
    private Integer strike;

    @JsonProperty("type")
    private String type;

    @JsonProperty("volume")
    private Long volume;
}
