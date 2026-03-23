package com.pietrader.broker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String  status;       // SUCCESS / FAILED / PAPER
    private String  orderId;
    private String  symbol;
    private String  strike;
    private String  direction;
    private Double  price;
    private Integer quantity;
    private String  errorMessage;
    private long    timestamp;
}
