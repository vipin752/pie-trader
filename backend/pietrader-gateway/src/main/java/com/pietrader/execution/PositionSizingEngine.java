package com.pietrader.execution;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.risk.RiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PIE TRADER — PositionSizingEngine
 * NOTE: ProbabilityMatrixDTO has no winProbability field — sizing is
 * confidence-tier only + consecutive-loss multiplier from RiskManager.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionSizingEngine {

    private final RiskManager riskManager;

    @Value("${trading.lot.size:75}")                    private int    defaultLotSize;
    @Value("${trading.sizing.base.lots:1}")             private int    baseLots;
    @Value("${trading.sizing.max.lots:3}")              private int    maxLots;
    @Value("${trading.sizing.high.confidence:80}")      private int    highConfidenceThreshold;
    @Value("${trading.sizing.very.high.confidence:90}") private int    veryHighConfidenceThreshold;

    public int calculate(String symbol, int confidence, OptionAnalyticsDTO dto) {
        int lots;
        if      (confidence >= veryHighConfidenceThreshold) lots = Math.min(baseLots * 3, maxLots);
        else if (confidence >= highConfidenceThreshold)     lots = Math.min(baseLots * 2, maxLots);
        else                                                lots = baseLots;

        double mult = riskManager.getSizingMultiplier(symbol);
        if (mult < 1.0) lots = Math.max(1, (int) Math.floor(lots * mult));

        log.info("📊 [{}] PositionSizing → confidence={} multiplier={} lots={}",
            symbol, confidence, mult, lots);
        return lots;
    }
}
