package com.pietrader.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * SSL Configuration — fixes "PKIX path building failed" for Angel API.
 *
 * Angel API endpoints affected:
 *   https://apiconnect.angelbroking.com  (login, REST orders)
 *   https://margincalculator.angelbroking.com  (contract master download)
 *   wss://smartapisocket.angelone.in  (WebSocket)
 *
 * Solution: apply trust-all TrustManager BEFORE Spring starts.
 * Called from PietraderGatewayApplication.main() before SpringApplication.run().
 *
 * This sets:
 *   1. HttpsURLConnection.setDefaultSSLSocketFactory  → covers login + REST + URL downloads
 *   2. SSLContext.setDefault()                        → covers Java-WebSocket library
 *   3. HttpsURLConnection.setDefaultHostnameVerifier  → covers hostname mismatch
 *
 * NOTE: This disables certificate verification entirely.
 * For production hardening: add Angel's CA cert to src/main/resources/ssl/angel-ca.crt
 * and load it into a custom TrustStore instead.
 */
@Configuration
@Slf4j
public class SslConfig {

    /**
     * Must be called BEFORE SpringApplication.run() in main().
     * Applies globally to all JVM SSL connections.
     */
    public static void applyGlobalSslBypass() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());

            // 1. All HttpsURLConnection calls (login, REST, contract download)
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            // 2. All new SSLContext instances (Java-WebSocket library picks this up)
            SSLContext.setDefault(sc);

            log.info("✅ SSL bypass active — Angel API accessible");

        } catch (Exception e) {
            log.error("❌ SSL bypass setup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * RestTemplate that trusts all SSL certificates.
     * Used by AngelBrokerAdapter and AngelAuthService for REST order calls.
     */
    @Bean(name = "angelRestTemplate")
    public RestTemplate angelRestTemplate() {
        // applyGlobalSslBypass() already set the default SSLSocketFactory,
        // so a plain RestTemplate will use it automatically.
        // No extra configuration needed.
        return new RestTemplate();
    }
}
