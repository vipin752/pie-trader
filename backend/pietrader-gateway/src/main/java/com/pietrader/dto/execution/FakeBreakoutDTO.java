package com.pietrader.dto.execution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIX: Renamed isFakeBreakout → fakeBreakout.
 * Same Lombok Boolean isXxx conflict as SessionDTO.
 * @JsonProperty("is_fake_breakout") keeps Python mapping correct.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FakeBreakoutDTO {

    @JsonProperty("is_fake_breakout")
    private Boolean fakeBreakout;   // was: isFakeBreakout

    @JsonProperty("reason")
    private String reason;
}
