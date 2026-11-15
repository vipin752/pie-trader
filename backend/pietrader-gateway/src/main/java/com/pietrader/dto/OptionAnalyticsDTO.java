package com.pietrader.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.decision.CompleteDecisionDTO;
import com.pietrader.dto.execution.ExecutionDebugDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.flow.InstitutionalFlowDTO;
import com.pietrader.dto.liquidity.LiquidityMapDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.MarketStructureDTO;
import com.pietrader.dto.risk.ExitManagementDTO;
import com.pietrader.dto.risk.PositionManagementDTO;
import com.pietrader.dto.risk.RiskManagementDTO;
import com.pietrader.dto.risk.ScalingDTO;
import com.pietrader.dto.signal.PremiumIntelligenceDTO;
import com.pietrader.dto.signal.StrikeOptimizerDTO;
import com.pietrader.dto.signal.StrikeSelectionDTO;
import com.pietrader.dto.signal.TradeSignalDTO;
import com.pietrader.dto.volatility.HistoricalContextDTO;
import com.pietrader.dto.volatility.VolatilityContextDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionAnalyticsDTO {

    @JsonProperty("trade_signal")
    private TradeSignalDTO tradeSignal;

    @JsonProperty("premium_intelligence")
    private PremiumIntelligenceDTO premiumIntelligence;

    @JsonProperty("strike_selection")
    private StrikeSelectionDTO strikeSelection;

    @JsonProperty("strike_optimizer")
    private StrikeOptimizerDTO strikeOptimizer;

    @JsonProperty("dealer_positioning")
    private DealerPositioningDTO dealerPositioning;

    @JsonProperty("market_structure")
    private MarketStructureDTO marketStructure;

    @JsonProperty("liquidity_map")
    private LiquidityMapDTO liquidityMap;

    @JsonProperty("institutional_flow")
    private InstitutionalFlowDTO institutionalFlow;

    @JsonProperty("volatility_context")
    private VolatilityContextDTO volatilityContext;

    @JsonProperty("market_context")
    private MarketContextDTO marketContext;

    @JsonProperty("historical_context")
    private HistoricalContextDTO historicalContext;

    @JsonProperty("execution_debug")
    private ExecutionDebugDTO executionDebug;

    @JsonProperty("execution_layer")
    private ExecutionLayerDTO executionLayer;

    @JsonProperty("confidence")
    private ConfidenceDTO confidence;

    @JsonProperty("auto_trade_decision")
    private AutoTradeDecisionDTO autoTradeDecision;

    @JsonProperty("adaptive_params")
    private AdaptiveParamsDTO adaptiveParams;

    @JsonProperty("execution_timing")
    private ExecutionTimingDTO executionTiming;

    @JsonProperty("risk_management")
    private RiskManagementDTO riskManagement;

    @JsonProperty("position_management")
    private PositionManagementDTO positionManagement;

    @JsonProperty("scaling")
    private ScalingDTO scaling;

    @JsonProperty("exit_management")
    private ExitManagementDTO exitManagement;

    @JsonProperty("complete_decision")
    private CompleteDecisionDTO completeDecision;

    @JsonProperty("ui_view")
    private CompleteDecisionDTO uiView;
}
