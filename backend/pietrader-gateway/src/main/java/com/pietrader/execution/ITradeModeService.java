package com.pietrader.execution;

import com.pietrader.execution.model.TradeMode;

/** Manages trading mode (PAPER/MANUAL/SEMI_AUTO/AUTO). Impl: ModeManagerImpl */
public interface ITradeModeService {
    TradeMode currentMode();
    boolean   isManualMode();
    boolean   isPaperMode();
    void      setMode(TradeMode mode);
    void      setMode(String mode);
}
