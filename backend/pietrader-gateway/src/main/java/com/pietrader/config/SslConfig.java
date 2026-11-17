package com.pietrader.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * SSL Configuration.
 *
 * CR-001.D fix: this class used to apply a trust-all TrustManager and a
 * hostname verifier that accepted any host, globally, for the entire JVM
 * (HttpsURLConnection.setDefaultSSLSocketFactory + SSLContext.setDefault +
 * setDefaultHostnameVerifier), called unconditionally from
 * PietraderGatewayApplication.main() before Spring even started. That
 * disabled certificate verification for every HTTPS connection this
 * process makes, not just calls to Angel One — a man-in-the-middle
 * vulnerability, present in every environment, with no way to turn it off.
 *
 * It existed to work around a "PKIX path building failed" error reported
 * against Angel One's endpoints. The correct fix for that specific error
 * (if it recurs against a real broker connection — not verified in this
 * sandbox, which has no network path to Angel One) is to add Angel's CA
 * or the missing intermediate certificate to a dedicated truststore, e.g.:
 *
 *   keytool -importcert -alias angelone -file angel-ca.crt \
 *       -keystore /path/to/truststore.jks
 *
 * and point the JVM at it with -Djavax.net.ssl.trustStore, or load it into
 * a Spring-managed SSLContext scoped only to the Angel HTTP client. Do not
 * reintroduce a global or trust-all bypass.
 */
@Configuration
@Slf4j
public class SslConfig {

    /**
     * RestTemplate used by AngelBrokerAdapter and AngelAuthService for
     * REST order calls. Standard JVM certificate verification now applies —
     * no custom SSLContext is installed.
     */
    @Bean(name = "angelRestTemplate")
    public RestTemplate angelRestTemplate() {
        return new RestTemplate();
    }
}
