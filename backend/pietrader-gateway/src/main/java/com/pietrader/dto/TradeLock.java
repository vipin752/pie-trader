package com.pietrader.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeLock {
    private boolean locked;
    private String strategy;
    private long timestamp;
}
