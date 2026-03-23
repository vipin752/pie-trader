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
public class ExpiryDTO {

    @JsonProperty("nearest_expiry")
    private String nearestExpiry;

    @JsonProperty("days_to_expiry")
    private Integer daysToExpiry;

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("tte_years")
    private Double tteYears;
}
