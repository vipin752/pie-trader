package com.pietrader.mapper;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.execution.ExecutionDebugDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.execution.FinalExecutionDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps execution_debug, execution_layer, execution_timing → TradeSignalEntity.
 *
 * Source: dto.getExecutionDebug(), dto.getExecutionLayer(), dto.getExecutionTiming()
 * Fields: tradingWindow, executionReady, entrySignal,
 *         entryType, isFakeBreakout, breakoutStatus, breakoutTriggerPrice
 */
@Component
@Slf4j
public class ExecutionMapper implements SectionMapper {

    @Override
    public void map(OptionAnalyticsDTO dto, TradeSignalEntity entity) {
        mapExecutionDebug(dto.getExecutionDebug(), entity);
        mapExecutionLayer(dto.getExecutionLayer(), entity);
        mapExecutionTiming(dto.getExecutionTiming(), entity);
    }

    private void mapExecutionDebug(ExecutionDebugDTO debug, TradeSignalEntity entity) {
        if (debug == null) return;

        if (debug.getTradingWindow() != null) {
            entity.setTradingWindow(debug.getTradingWindow().getWindow());
        }
        if (debug.getFakeBreakout() != null) {
            entity.setIsFakeBreakout(debug.getFakeBreakout().getFakeBreakout());
        }
    }

    private void mapExecutionLayer(ExecutionLayerDTO layer, TradeSignalEntity entity) {
        if (layer == null) return;

        FinalExecutionDTO finalExec = layer.getFinalExecution();
        if (finalExec != null) {
            entity.setExecutionReady(finalExec.getExecutionReady());
        }

        if (layer.getBreakout() != null) {
            entity.setBreakoutStatus(layer.getBreakout().getStatus());
            entity.setBreakoutTriggerPrice(layer.getBreakout().getTriggerPrice());
        }
    }

    private void mapExecutionTiming(ExecutionTimingDTO timing, TradeSignalEntity entity) {
        if (timing == null) return;

        entity.setEntrySignal(timing.getEntrySignal());
        entity.setEntryType(timing.getEntryType());
    }
}
