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
public class PremiumIntelligenceDTO {

    @JsonProperty("premium_activity")
    private String premiumActivity;

    @JsonProperty("best_strike")
    private Integer bestStrike;

    @JsonProperty("confidence")
    private String confidence;

    @JsonProperty("top_strikes")
    private List<StrikeScoreDTO> topStrikes;
}
