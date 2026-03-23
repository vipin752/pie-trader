package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.JsonNode;

/** Angel One REST API client. Impl: AngelRestClientImpl */
public interface IAngelRestClient {
    JsonNode post(String apiPath, String body);
    JsonNode get(String apiPath);
}
