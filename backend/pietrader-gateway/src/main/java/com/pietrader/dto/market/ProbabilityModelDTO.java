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
public class ProbabilityModelDTO {

    @JsonProperty("afternoon_session_13_45_14_45")
    private SessionProbabilityDTO afternoonSession;
}
