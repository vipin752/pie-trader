package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.signal.StrikeCandidateDTO;
import com.pietrader.dto.signal.StrikeSelectionDTO;
import com.pietrader.execution.BreakoutTriggerEngine;
import com.pietrader.execution.TradeStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — TradeCardMapperTest
 *
 * Covers the computed risk fields (stop_loss_price/target_price/
 * risk_reward_ratio/max_loss_inr/potential_return_inr) the UI trade-card
 * contract needs: a real per-strike stop/target/potential-return, not just
 * the pre-existing descriptive strings ("RR 1:2", "1% capital").
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TradeCardMapper — computed risk levels")
class TradeCardMapperTest {

    @Mock TradeStateMachine stateMachine;
    @Mock BreakoutTriggerEngine triggerEngine;

    private TradeCardMapper mapper;

    private static final int    LOT_SIZE = 75;
    private static final double SL_PCT   = 25.0;

    @BeforeEach
    void setUp() {
        mapper = new TradeCardMapper(stateMachine, triggerEngine);
        ReflectionTestUtils.setField(mapper, "lotSize", LOT_SIZE);
        ReflectionTestUtils.setField(mapper, "capital", 200000.0);
        ReflectionTestUtils.setField(mapper, "slPct",   SL_PCT);
    }

    private OptionAnalyticsDTO dtoWithLtp(double ltp) {
        StrikeCandidateDTO candidate = StrikeCandidateDTO.builder()
            .strike(24500).type("CE").ltp(ltp).iv(14.2).volume(1200L).build();
        StrikeSelectionDTO selection = StrikeSelectionDTO.builder()
            .selectedStrike("NIFTY24NOV24500CE")
            .topCandidates(List.of(candidate))
            .build();
        MarketContextDTO ctx = MarketContextDTO.builder().symbol("NIFTY").spot(24510.0).build();
        return OptionAnalyticsDTO.builder()
            .marketContext(ctx)
            .strikeSelection(selection)
            .build();
    }

    @Test
    @DisplayName("Computes stop/target/RR/potential-return from the selected option's LTP, per the 1:2 rule")
    void computesRealRiskLevelsFromLtp() {
        Map<String, Object> card = mapper.toTradeCard(dtoWithLtp(200.0));

        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) card.get("risk");

        // riskPerUnit = 200 * 0.25 = 50
        assertThat((Double) risk.get("stop_loss_price")).isEqualTo(150.0);   // 200 - 50
        assertThat((Double) risk.get("target_price")).isEqualTo(300.0);     // 200 + 2*50
        assertThat((Double) risk.get("risk_reward_ratio")).isEqualTo(2.0);
        assertThat((Double) risk.get("max_loss_inr")).isEqualTo(50.0 * LOT_SIZE);
        assertThat((Double) risk.get("potential_return_inr")).isEqualTo(100.0 * LOT_SIZE);

        // The pre-existing descriptive strings must still be present, unchanged.
        assertThat(risk.get("sl")).isEqualTo("Option SL 25%");
        assertThat(risk.get("target")).isEqualTo("RR 1:2");
    }

    @Test
    @DisplayName("No selected option LTP → computed risk fields are safely zero, not an exception")
    void noSelectedLtp_fieldsAreZero() {
        OptionAnalyticsDTO dto = OptionAnalyticsDTO.builder()
            .marketContext(MarketContextDTO.builder().symbol("NIFTY").build())
            .build();

        Map<String, Object> card = mapper.toTradeCard(dto);

        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) card.get("risk");

        assertThat((Double) risk.get("stop_loss_price")).isEqualTo(0.0);
        assertThat((Double) risk.get("target_price")).isEqualTo(0.0);
        assertThat((Double) risk.get("risk_reward_ratio")).isEqualTo(0.0);
        assertThat((Double) risk.get("max_loss_inr")).isEqualTo(0.0);
        assertThat((Double) risk.get("potential_return_inr")).isEqualTo(0.0);
        assertThat(card.get("status")).isEqualTo("OK");
    }

    @Test
    @DisplayName("buildOption's own ltp field matches the computed risk fields' basis")
    void optionLtpMatchesRiskBasis() {
        Map<String, Object> card = mapper.toTradeCard(dtoWithLtp(186.6));

        @SuppressWarnings("unchecked")
        Map<String, Object> option = (Map<String, Object>) card.get("option");
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) card.get("risk");

        double ltp = (Double) option.get("ltp");
        assertThat(ltp).isEqualTo(186.6);
        double riskPerUnit = ltp * (SL_PCT / 100.0);
        assertThat((Double) risk.get("stop_loss_price")).isEqualTo(ltp - riskPerUnit, org.assertj.core.data.Offset.offset(0.01));
        assertThat((Double) risk.get("target_price")).isEqualTo(ltp + 2 * riskPerUnit, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Null DTO → NO_DATA status, never throws")
    void nullDto_returnsNoData() {
        Map<String, Object> card = mapper.toTradeCard(null);
        assertThat(card.get("status")).isEqualTo("NO_DATA");
    }
}
