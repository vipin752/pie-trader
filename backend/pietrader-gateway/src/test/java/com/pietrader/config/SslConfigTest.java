package com.pietrader.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — SslConfigTest (CR-001.D regression)
 *
 * The previous version of this test class affirmed the dangerous
 * behaviour as correct: it asserted applyGlobalSslBypass() installed a
 * hostname verifier that accepted ANY host and a non-default SSL socket
 * factory, i.e. it tested that certificate verification was successfully
 * disabled. That method no longer exists. These tests instead confirm
 * SslConfig now does the one safe thing it still needs to: provide a
 * plain RestTemplate, with no custom SSLContext installed anywhere.
 */
@DisplayName("SslConfig — standard TLS verification (no bypass)")
class SslConfigTest {

    @Test
    @DisplayName("angelRestTemplate: builds a plain RestTemplate")
    void angelRestTemplate_isPlainRestTemplate() {
        RestTemplate template = new SslConfig().angelRestTemplate();
        assertThat(template).isNotNull();
        assertThat(template).isExactlyInstanceOf(RestTemplate.class);
    }

    @Test
    @DisplayName("No method on SslConfig can disable certificate verification")
    void noGlobalSslBypassMethodExists() {
        boolean hasBypassMethod = java.util.Arrays.stream(SslConfig.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().toLowerCase().contains("bypass")
                || m.getName().toLowerCase().contains("disablessl")
                || m.getName().toLowerCase().contains("trustall"));
        assertThat(hasBypassMethod)
            .as("SslConfig must never regain a method that disables certificate verification")
            .isFalse();
    }
}
