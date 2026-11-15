package com.pietrader.broker.angel;

import java.util.List;
import java.util.Map;

/** Angel contract master — token lookup, expiry, strike range. Impl: AngelTokenServiceImpl */
public interface IAngelTokenService {
    void                     loadMasterContract();
    List<Map<String, Object>> loadFromUrl();
    AngelInstrument          getTokenInfo(String token);
    List<AngelInstrument>    getOptions(String symbol);
    String                   getNearestExpiry(String symbol);
    List<String>             getTokensForStrikeRange(String symbol, int atm, int range);
    String                   findToken(String symbol, String strikeWithType);
    String                   findTradingSymbol(String symbol, String strikeWithType);
}
