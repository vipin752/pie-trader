package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Angel One Session Manager.
 *
 * LOGIN FLOW:
 *   On startup → reads TOTP from stdin (one-time input).
 *   Stores JWT + feedToken in memory.
 *   Auto-refreshes JWT every 20h via background thread.
 *
 * NOT a @PostConstruct — called explicitly by AngelStartupRunner
 * so Spring context is fully ready before blocking on stdin.
 */
@Service
@Slf4j
@Getter
public class AngelSessionManager {

    @Value("${angel.api.key}")        private String apiKey;
    @Value("${angel.client.id}")      private String clientId;
    @Value("${angel.password}")       private String password;
    @Value("${angel.client.local.ip:10.0.0.1}")  private String localIp;
    @Value("${angel.client.public.ip:127.0.0.1}") private String publicIp;
    @Value("${angel.mac.address:AA:BB:CC:DD:EE:FF}") private String macAddress;
    @Value("${angel.totp.secret:NA}") private String totpSecret;

    private String  jwtToken;
    private String  refreshToken;
    private String  feedToken;

    public String getTotpSecret() { return totpSecret; }
    private long    lastLoginTime = 0;

    private static final String BASE_URL = "https://apiconnect.angelbroking.com";
    private final ObjectMapper  mapper   = new ObjectMapper();

    // ── LOGIN (called by AngelStartupRunner with TOTP from stdin) ─────────────
    public void login(String totp) {
        log.info("🔐 Angel login → clientId={}", clientId);
        try {
            String body = String.format(
                "{\"clientcode\":\"%s\",\"password\":\"%s\",\"totp\":\"%s\"}",
                clientId, password, totp
            );

            String response = post(
                BASE_URL + "/rest/auth/angelbroking/user/v1/loginByPassword",
                body
            );

            JsonNode root = mapper.readTree(response);
            if (!root.path("status").asBoolean()) {
                throw new RuntimeException("Login failed: " + root.path("message").asText());
            }

            JsonNode data = root.path("data");
            jwtToken     = data.path("jwtToken").asText();
            refreshToken = data.path("refreshToken").asText();
            feedToken    = data.path("feedToken").asText();
            lastLoginTime = System.currentTimeMillis();

            log.info("✅ Angel login success. feedToken acquired.");

        } catch (Exception e) {
            log.error("❌ Angel login failed: {}", e.getMessage());
            throw new RuntimeException("Angel login failed", e);
        }
    }

    // ── REFRESH JWT ───────────────────────────────────────────────────────────
    public void refreshJwt() {
        log.info("🔄 Refreshing JWT...");
        try {
            String response = postWithAuth(
                BASE_URL + "/rest/auth/angelbroking/jwt/v1/generateTokens",
                "{}"
            );

            JsonNode root = mapper.readTree(response);
            if (root.path("status").asBoolean()) {
                jwtToken     = root.path("data").path("jwtToken").asText();
                refreshToken = root.path("data").path("refreshToken").asText();
                lastLoginTime = System.currentTimeMillis();
                log.info("✅ JWT refreshed");
            } else {
                log.warn("JWT refresh failed — re-login required");
            }
        } catch (Exception e) {
            log.error("❌ JWT refresh error: {}", e.getMessage());
        }
    }

    // ── SESSION MAINTENANCE LOOP ──────────────────────────────────────────────
    public void startSessionMaintenance() {
        Thread.ofVirtual().name("AngelSessionMaintainer").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5 * 60 * 1000L); // check every 5 min
                    long ageMs = System.currentTimeMillis() - lastLoginTime;
                    if (ageMs > 20 * 60 * 60 * 1000L) { // 20 hours
                        refreshJwt();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Session maintenance error: {}", e.getMessage());
                }
            }
        });
    }

    // ── HTTP HELPERS ──────────────────────────────────────────────────────────
    private String post(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-PrivateKey", apiKey);
        conn.setRequestProperty("X-UserType", "USER");
        conn.setRequestProperty("X-SourceID", "WEB");
        conn.setRequestProperty("X-ClientLocalIP", localIp);
        conn.setRequestProperty("X-ClientPublicIP", publicIp);
        conn.setRequestProperty("X-MACAddress", macAddress);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    }

    private String postWithAuth(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
        conn.setRequestProperty("X-PrivateKey", apiKey);
        conn.setRequestProperty("X-UserType", "USER");
        conn.setRequestProperty("X-SourceID", "WEB");
        conn.setRequestProperty("X-ClientLocalIP", localIp);
        conn.setRequestProperty("X-ClientPublicIP", publicIp);
        conn.setRequestProperty("X-MACAddress", macAddress);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400
                ? conn.getInputStream() : conn.getErrorStream();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
