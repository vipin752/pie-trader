package com.pietrader.dto.decision;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pietrader.dto.market.SessionProbabilityDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbabilityMatrixDTO {

    @JsonProperty("afternoon_session_13_45_14_45")
    private SessionProbabilityDTO afternoonSession;
}
