package com.pietrader.journal.model;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class JournalExitEntry {
    private String symbol;
    private double exitPrice;
    private String exitReason;
    private double pnl;
    private long   exitAt;
}
