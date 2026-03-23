package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AngelHttpClient {

    private final AngelSessionManager sessionManager;
    private final AngelApiConfig apiConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode post(String path, String jsonBody) {
	try {
	    URL url = new URL(apiConfig.getBaseUrl() + path);
	    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

	    conn.setRequestMethod("POST");
	    conn.setRequestProperty("Content-Type", "application/json");
	    conn.setRequestProperty("Accept", "application/json");
	    conn.setRequestProperty("X-PrivateKey", sessionManager.getApiKey());
	    conn.setRequestProperty("X-UserType", "USER");
	    conn.setRequestProperty("X-SourceID", "WEB");
	    conn.setRequestProperty("X-ClientLocalIP", sessionManager.getLocalIp());
	    conn.setRequestProperty("X-ClientPublicIP", sessionManager.getPublicIp());
	    conn.setRequestProperty("X-MACAddress", sessionManager.getMacAddress());
	    conn.setRequestProperty("Authorization", "Bearer " + sessionManager.getJwtToken());

	    conn.setDoOutput(true);

	    try (OutputStream os = conn.getOutputStream()) {
		os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
	    }

	    InputStream is = conn.getInputStream();
	    return mapper.readTree(is);

	} catch (Exception e) {
	    e.printStackTrace();
	    return null;
	}
    }
}
