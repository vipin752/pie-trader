package com.pietrader.dto.decision;
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
public class TradingCardDTO {

    @JsonProperty("spot")
    private Double spot;

    @JsonProperty("fear_index")
    private Double fearIndex;

    @JsonProperty("session_1")
    private String session1;

    @JsonProperty("session_3")
    private String session3;

    @JsonProperty("btst")
    private String btst;
}
