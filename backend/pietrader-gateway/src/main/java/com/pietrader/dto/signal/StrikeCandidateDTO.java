package com.pietrader.dto.signal;
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
public class StrikeCandidateDTO {

    @JsonProperty("strike")
    private Integer strike;

    @JsonProperty("type")
    private String type;

    @JsonProperty("score")
    private Double score;

    @JsonProperty("ltp")
    private Double ltp;

    @JsonProperty("volume")
    private Long volume;

    @JsonProperty("iv")
    private Double iv;
}
