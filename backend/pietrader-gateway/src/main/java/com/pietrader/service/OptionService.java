package com.pietrader.service;

import com.pietrader.dto.OptionAnalyticsDTO;

public interface OptionService {
    String getOptionSummary(String symbol);
    OptionAnalyticsDTO getLatestSignal(String symbol);
}
