package com.pietrader.broker.angel;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SubscribeRequest {
    private String feedToken;
    private List<String> tokens;
}
