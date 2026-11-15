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
public class CompressionDTO {

    @JsonProperty("compression_detected")
    private Boolean compressionDetected;

    @JsonProperty("wall_spread_pct")
    private Double wallSpreadPct;

    @JsonProperty("flip_distance_pct")
    private Double flipDistancePct;

    @JsonProperty("breakout_signal")
    private String breakoutSignal;
}
