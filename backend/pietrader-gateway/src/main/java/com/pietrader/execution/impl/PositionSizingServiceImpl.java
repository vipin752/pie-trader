package com.pietrader.execution.impl;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.IPositionSizingService;
import com.pietrader.risk.IRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PositionSizingServiceImpl implements IPositionSizingService {

    private final IRiskService riskManager;

    @Value("${trading.sizing.base.lots:1}")             private int baseLots;
    @Value("${trading.sizing.max.lots:3}")              private int maxLots;
    @Value("${trading.sizing.high.confidence:80}")      private int highConfThreshold;
    @Value("${trading.sizing.very.high.confidence:90}") private int veryHighConfThreshold;

    @Override
    public int calculate(String symbol, int confidence, OptionAnalyticsDTO dto) {
        int lots;
        if      (confidence >= veryHighConfThreshold) lots = Math.min(baseLots * 3, maxLots);
        else if (confidence >= highConfThreshold)     lots = Math.min(baseLots * 2, maxLots);
        else                                          lots = baseLots;

        double mult = riskManager.getSizingMultiplier(symbol);
        if (mult < 1.0) lots = Math.max(1, (int) Math.floor(lots * mult));

        log.info("📊 [{}] Sizing → conf={} mult={} lots={}", symbol, confidence, mult, lots);
        return lots;
    }
}
