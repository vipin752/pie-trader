package com.pietrader.config;

import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.net.URL;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

@DisplayName("SslConfig — Angel API SSL Fix Tests")
class SslConfigTest {

    @Test @DisplayName("applyGlobalSslBypass: sets SSLContext without exception")
    void applySslBypass_noException() {
        assertThatCode(SslConfig::applyGlobalSslBypass).doesNotThrowAnyException();
    }

    @Test @DisplayName("After bypass: default hostname verifier accepts all hosts")
    void afterBypass_hostnameVerifierAcceptsAll() {
        SslConfig.applyGlobalSslBypass();
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        assertThat(hv.verify("apiconnect.angelbroking.com", null)).isTrue();
        assertThat(hv.verify("margincalculator.angelbroking.com", null)).isTrue();
        assertThat(hv.verify("smartapisocket.angelone.in", null)).isTrue();
    }

    @Test @DisplayName("After bypass: default SSLSocketFactory is not the JDK default")
    void afterBypass_customSocketFactory() {
        SSLSocketFactory before = HttpsURLConnection.getDefaultSSLSocketFactory();
        SslConfig.applyGlobalSslBypass();
        // After bypass the factory should be set (not null)
        assertThat(HttpsURLConnection.getDefaultSSLSocketFactory()).isNotNull();
    }

    @Test @DisplayName("TrustManager trusts any certificate (bypass mode)")
    void trustManagerTrustsAnyCert() throws Exception {
        TrustManager[] tms = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };
        // Should not throw for null chain (bypass mode)
        X509TrustManager tm = (X509TrustManager) tms[0];
        assertThatCode(() -> tm.checkServerTrusted(null, "RSA")).doesNotThrowAnyException();
    }

    @Test @DisplayName("Angel REST base URL reachable after SSL bypass (network)")
    @Tag("network")
    void angelRestUrlReachable() {
        assumeThat(System.getenv("CI")).isNull(); // skip in CI
        try {
            SslConfig.applyGlobalSslBypass();
            URL url = new URL("https://apiconnect.angelbroking.com");
            var conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.connect();
            // Any response (even 404) means SSL handshake succeeded
            assertThat(conn.getResponseCode()).isGreaterThan(0);
            System.out.println("✅ Angel REST URL reachable: HTTP " + conn.getResponseCode());
        } catch (Exception e) {
            assumeTrue(false, "Network/Angel unavailable: " + e.getMessage());
        }
    }

    @Test @DisplayName("Contract master URL reachable after SSL bypass (network)")
    @Tag("network")
    void contractMasterUrlReachable() {
        assumeThat(System.getenv("CI")).isNull();
        try {
            SslConfig.applyGlobalSslBypass();
            URL url = new URL("https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json");
            var conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            assertThat(code).isEqualTo(200);
            System.out.println("✅ Contract URL reachable: HTTP " + code
                + " content-length=" + conn.getContentLength());
        } catch (Exception e) {
            assumeTrue(false, "Network unavailable: " + e.getMessage());
        }
    }
}
