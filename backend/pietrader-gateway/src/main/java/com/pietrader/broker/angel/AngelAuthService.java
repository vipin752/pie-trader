package com.pietrader.broker.angel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Angel One Auth — JWT token with auto-refresh.
 * TODO: Replace hardcoded TOTP with TOTP library (e.g. com.warrenstrange:googleauth)
 */
@Service
@Slf4j
public class AngelAuthService {

    @Value("${angel.api.key}")    private String apiKey;
    @Value("${angel.client.id}")  private String clientId;
    @Value("${angel.password}")   private String password;
    @Value("${angel.base.url}")   private String baseUrl;
    @Value("${angel.totp.secret:NA}") private String totpSecret;

    private String  jwtToken;
    private Instant tokenExpiry;

    public String getToken() {
        if (jwtToken == null || isExpired()) return login();
        return jwtToken;
    }

    private boolean isExpired() {
        return tokenExpiry != null && Instant.now().isAfter(tokenExpiry);
    }

    public String login() {
        try {
            String url = baseUrl + "/rest/auth/angelbroking/user/v1/loginByPassword";
            RestTemplate rest = new RestTemplate();

            // TOTP: generate dynamically if secret available, else use placeholder
            String totp = generateTotp(totpSecret);

            Map<String, String> req = new HashMap<>();
            req.put("clientcode", clientId);
            req.put("password",   password);
            req.put("totp",       totp);

            Map response = rest.postForObject(url, req, Map.class);
            Map data = (Map) response.get("data");

            jwtToken    = (String) data.get("jwtToken");
            tokenExpiry = Instant.now().plusSeconds(3600); // 1h TTL

            log.info("✅ Angel One login success. Token valid for 1h.");
            return jwtToken;

        } catch (Exception e) {
            log.error("❌ Angel One login failed", e);
            throw new RuntimeException("Angel login failed: " + e.getMessage());
        }
    }

    /**
     * Generate TOTP — replace with GoogleAuth library in production.
     * com.warrenstrange:googleauth:1.4.0 → GoogleAuthenticator().getTotpPassword(secret)
     */
    private String generateTotp(String secret) {
        if (secret == null || "NA".equals(secret)) {
            log.warn("⚠️ TOTP secret not configured — using placeholder");
            return "123456";
        }
        // TODO: return new GoogleAuthenticator().getTotpPassword(secret).toString();
        return "123456";
    }
}
