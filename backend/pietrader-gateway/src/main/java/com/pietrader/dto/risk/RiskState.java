package com.pietrader.dto.risk;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskState {

    private double pnl;
    private double maxLoss;
    private int tradesTaken;
}
