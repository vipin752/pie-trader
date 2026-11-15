package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AngelRestClient {

    private final AngelApiConfig apiConfig;
    private final AngelSessionManager sessionManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode post(String apiPath, String body) {
	try {
	    String url = apiConfig.getBaseUrl() + apiPath;

	    String response = Request.post(url)
		    .addHeader("Accept", "application/json")
		    .addHeader("Content-Type", "application/json")
		    .addHeader("X-PrivateKey", sessionManager.getApiKey())
		    .addHeader("X-UserType", "USER")
		    .addHeader("X-SourceID", "WEB")
		    .addHeader("X-ClientLocalIP", sessionManager.getLocalIp())
		    .addHeader("X-ClientPublicIP", sessionManager.getPublicIp())
		    .addHeader("X-MACAddress", sessionManager.getMacAddress())
		    .addHeader("Authorization", "Bearer " + sessionManager.getJwtToken())
		    .bodyString(body, ContentType.APPLICATION_JSON)
		    .execute()
		    .returnContent()
		    .asString();

	    return mapper.readTree(response);

	} catch (Exception e) {
	    log.error("Angel POST API error", e);
	    return null;
	}
    }

    public JsonNode get(String apiPath) {
	try {
	    String url = apiConfig.getBaseUrl() + apiPath;

	    String response = Request.get(url)
		    .addHeader("Accept", "application/json")
		    .addHeader("X-PrivateKey", sessionManager.getApiKey())
		    .addHeader("X-UserType", "USER")
		    .addHeader("X-SourceID", "WEB")
		    .addHeader("X-ClientLocalIP", sessionManager.getLocalIp())
		    .addHeader("X-ClientPublicIP", sessionManager.getPublicIp())
		    .addHeader("X-MACAddress", sessionManager.getMacAddress())
		    .addHeader("Authorization", "Bearer " + sessionManager.getJwtToken())
		    .execute()
		    .returnContent()
		    .asString();

	    return mapper.readTree(response);

	} catch (Exception e) {
	    log.error("Angel GET API error", e);
	    return null;
	}
    }
}
