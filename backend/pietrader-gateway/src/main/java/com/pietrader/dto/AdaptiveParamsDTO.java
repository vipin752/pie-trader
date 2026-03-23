package com.pietrader.dto;

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
public class AdaptiveParamsDTO {

    @JsonProperty("flip_distance_threshold")
    private Double flipDistanceThreshold;

    // JSON sends integer e.g. 70 — Integer is safe here
    @JsonProperty("confidence_threshold")
    private Integer confidenceThreshold;

    // JSON sends integer e.g. 5 — Integer is safe here
    @JsonProperty("volume_threshold")
    private Integer volumeThreshold;

    // ✅ FIX: JSON sends 2.0 (float) — must be Double not Integer
    @JsonProperty("rr_target")
    private Double rrTarget;
}
