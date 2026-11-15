package com.pietrader.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIX: Renamed isMarket → marketOpen.
 * Lombok @Data + Boolean isMarket generates getIsMarket() AND isIsMarket()
 * causing Jackson "Conflicting getter definitions" error.
 * @JsonProperty("is_market") keeps Python JSON mapping correct.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SessionDTO {

    @JsonProperty("session")
    private String session;

    @JsonProperty("time_ist")
    private String timeIst;

    @JsonProperty("is_market")
    private Boolean marketOpen;   // was: isMarket
}
