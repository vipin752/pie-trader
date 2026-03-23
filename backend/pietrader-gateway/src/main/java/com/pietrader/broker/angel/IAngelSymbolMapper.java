package com.pietrader.broker.angel;

import java.util.Map;

/** Maps symbol/strike/type to Angel instrument token. Impl: AngelSymbolMapperImpl */
public interface IAngelSymbolMapper {
    void              load();
    Map<String, String> findOption(String symbol, String strike, String type);
}
