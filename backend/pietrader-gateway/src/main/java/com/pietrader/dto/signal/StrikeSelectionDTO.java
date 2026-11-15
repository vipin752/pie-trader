package com.pietrader.dto.signal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrikeSelectionDTO {

    @JsonProperty("selected_strike")
    private String selectedStrike;

    @JsonProperty("confidence")
    private String confidence;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("top_candidates")
    private List<StrikeCandidateDTO> topCandidates;

    @JsonProperty("optimized")
    private Boolean optimized;

    @JsonProperty("base_strike")
    private String baseStrike;
}
