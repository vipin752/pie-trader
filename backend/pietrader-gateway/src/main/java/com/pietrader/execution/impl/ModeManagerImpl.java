package com.pietrader.execution.impl;

import com.pietrader.execution.ITradeModeService;
import com.pietrader.execution.model.TradeMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ModeManagerImpl implements ITradeModeService {

    private final AtomicReference<TradeMode> currentMode = new AtomicReference<>(TradeMode.PAPER);

    public ModeManagerImpl(@Value("${trading.mode:PAPER}") String configured) {
        try { currentMode.set(TradeMode.valueOf(configured.toUpperCase())); }
        catch (IllegalArgumentException e) { currentMode.set(TradeMode.PAPER); }
        log.info("⚙️ TradingMode = {}", currentMode.get());
    }

    @Override public TradeMode currentMode()  { return currentMode.get(); }
    @Override public boolean   isManualMode() { return currentMode.get() == TradeMode.MANUAL; }
    @Override public boolean   isPaperMode()  { return currentMode.get() == TradeMode.PAPER; }
    @Override public void      setMode(TradeMode mode) { TradeMode prev = currentMode.getAndSet(mode); log.warn("🔄 Mode {} → {}", prev, mode); }
    @Override public void      setMode(String s)       { setMode(TradeMode.valueOf(s.toUpperCase())); }
}
