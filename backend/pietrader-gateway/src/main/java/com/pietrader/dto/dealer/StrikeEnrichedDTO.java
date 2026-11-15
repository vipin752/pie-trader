package com.pietrader.dto.dealer;
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
public class StrikeEnrichedDTO {

    @JsonProperty("strike")
    private Integer strike;

    @JsonProperty("call_oi")
    private Long callOi;

    @JsonProperty("put_oi")
    private Long putOi;

    @JsonProperty("call_volume")
    private Long callVolume;

    @JsonProperty("put_volume")
    private Long putVolume;

    @JsonProperty("call_iv")
    private Double callIv;

    @JsonProperty("put_iv")
    private Double putIv;

    @JsonProperty("call_ltp")
    private Double callLtp;

    @JsonProperty("put_ltp")
    private Double putLtp;

    @JsonProperty("call_gamma")
    private Double callGamma;

    @JsonProperty("put_gamma")
    private Double putGamma;
}
