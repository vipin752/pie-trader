package com.pietrader.dto.execution;
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
public class ExecutionDebugDTO {

    @JsonProperty("trading_window")
    private TradingWindowDTO tradingWindow;

    @JsonProperty("fake_breakout")
    private FakeBreakoutDTO fakeBreakout;
}
