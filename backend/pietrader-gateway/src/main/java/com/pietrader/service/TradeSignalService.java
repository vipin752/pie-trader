package com.pietrader.service;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.entity.TradeSignalEntity;

import java.util.List;
import java.util.Optional;

public interface TradeSignalService {
    void process(OptionAnalyticsDTO dto);
    Optional<TradeSignalEntity> getLatest(String symbol);
    List<TradeSignalEntity> getHistory(String symbol);
}
