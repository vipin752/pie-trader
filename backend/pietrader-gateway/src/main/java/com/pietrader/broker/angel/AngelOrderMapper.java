package com.pietrader.broker.angel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AngelOrderMapper {

    private final AngelTokenService tokenService;

    public String getToken(String symbol, String strikeWithType) {
	return tokenService.findToken(symbol, strikeWithType);
    }
}

