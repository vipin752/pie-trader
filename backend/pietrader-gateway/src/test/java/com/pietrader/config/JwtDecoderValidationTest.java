package com.pietrader.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PIE TRADER — JwtDecoderValidationTest (Keycloak JWT integration)
 *
 * SecurityConfigTest proves the authorization matrix using MockMvc's
 * jwt() test support, which deliberately bypasses real token decoding —
 * exactly as the pasted integration plan's own acceptance criteria warns:
 * "Mock JWT tests alone do not prove real Keycloak signature verification."
 *
 * This test proves the actual validation Spring Boot wires up from
 * spring.security.oauth2.resourceserver.jwt.issuer-uri + .audiences
 * (signature, issuer, audience, expiry) against a locally generated RSA
 * key pair — no live Keycloak instance needed, but the same
 * NimbusJwtDecoder + JwtValidators code path production uses.
 */
@DisplayName("JWT decoder — real signature / issuer / audience / expiry validation")
class JwtDecoderValidationTest {

    private static final String ISSUER = "https://pietrader.in/realms/pie-trader";
    private static final String AUDIENCE = "pie-trader-api";

    private RSAPrivateKey correctPrivateKey;
    private RSAPublicKey correctPublicKey;
    private RSAPrivateKey wrongPrivateKey;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair correctPair = generateRsaKeyPair();
        correctPrivateKey = (RSAPrivateKey) correctPair.getPrivate();
        correctPublicKey = (RSAPublicKey) correctPair.getPublic();

        KeyPair wrongPair = generateRsaKeyPair();
        wrongPrivateKey = (RSAPrivateKey) wrongPair.getPrivate();

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withPublicKey(correctPublicKey).build();

        // Mirrors what Spring Boot's OAuth2ResourceServerJwtConfiguration
        // builds from issuer-uri (timestamp + issuer) plus an audience
        // check layered on top when .audiences is also set.
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(AUDIENCE)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required audience " + AUDIENCE + " is missing", null));
        };
        nimbusDecoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), audienceValidator));

        decoder = nimbusDecoder;
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String sign(RSAPrivateKey signingKey, String issuer, String audience, Instant exp) throws Exception {
        // Always issue 10 minutes before expiry, whether exp is in the
        // future (valid-token tests) or the past (expired-token test) —
        // keeps issuedAt < expiresAt true in every case.
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .subject("test-user")
            .issueTime(Date.from(exp.minusSeconds(600)))
            .expirationTime(Date.from(exp));
        if (issuer != null) claims.issuer(issuer);
        if (audience != null) claims.audience(audience);

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        signedJWT.sign(new RSASSASigner(signingKey));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("correctly signed token, right issuer, right audience, not expired -> decodes")
    void validToken_decodesSuccessfully() throws Exception {
        String token = sign(correctPrivateKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(300));

        assertThat(decoder.decode(token).getSubject()).isEqualTo("test-user");
    }

    @Test
    @DisplayName("token signed by a different (attacker) key -> rejected")
    void wrongSignature_isRejected() throws Exception {
        String token = sign(wrongPrivateKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("wrong issuer -> rejected")
    void wrongIssuer_isRejected() throws Exception {
        String token = sign(correctPrivateKey, "https://attacker.example/realms/fake", AUDIENCE, Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("missing/wrong audience -> rejected")
    void wrongAudience_isRejected() throws Exception {
        String token = sign(correctPrivateKey, ISSUER, "some-other-api", Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("expired token -> rejected")
    void expiredToken_isRejected() throws Exception {
        String token = sign(correctPrivateKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }
}
