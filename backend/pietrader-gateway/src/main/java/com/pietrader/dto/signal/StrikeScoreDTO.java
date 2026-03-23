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
public class StrikeScoreDTO {

    @JsonProperty("strike")
    private Integer strike;

    @JsonProperty("score")
    private Double score;

    @JsonProperty("call_ltp")
    private Double callLtp;

    @JsonProperty("put_ltp")
    private Double putLtp;

    @JsonProperty("call_iv")
    private Double callIv;

    @JsonProperty("put_iv")
    private Double putIv;

    @JsonProperty("call_volume")
    private Long callVolume;

    @JsonProperty("put_volume")
    private Long putVolume;
}
