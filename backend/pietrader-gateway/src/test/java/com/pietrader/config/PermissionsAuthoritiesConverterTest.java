package com.pietrader.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — PermissionsAuthoritiesConverterTest (Keycloak JWT integration)
 *
 * Proves the real {@code permissions} claim -> Spring Security authority
 * mapping, independent of SecurityConfig's URL matchers (covered
 * separately in SecurityConfigTest).
 */
@DisplayName("PermissionsAuthoritiesConverter — permissions claim -> PERM_ authorities")
class PermissionsAuthoritiesConverterTest {

    private final PermissionsAuthoritiesConverter converter = new PermissionsAuthoritiesConverter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            claims
        );
    }

    @Test
    @DisplayName("array-form permissions claim becomes PERM_-prefixed authorities")
    void arrayFormClaim_becomesPrefixedAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("permissions", List.of("trader.read", "trader.execute")));

        List<String> authorities = converter.convert(jwt).stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        assertThat(authorities).containsExactlyInAnyOrder("PERM_trader.read", "PERM_trader.execute");
    }

    @Test
    @DisplayName("delimited-string-form permissions claim is also accepted (defensive fallback)")
    void stringFormClaim_isSplitAndPrefixed() {
        Jwt jwt = jwtWithClaims(Map.of("permissions", "trader.read, trader.manual"));

        List<String> authorities = converter.convert(jwt).stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        assertThat(authorities).containsExactlyInAnyOrder("PERM_trader.read", "PERM_trader.manual");
    }

    @Test
    @DisplayName("missing permissions claim yields no authorities (not an error)")
    void missingClaim_yieldsEmptyAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "some-user"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("blank/empty entries in the claim are dropped, never turned into a bare PERM_ authority")
    void blankEntries_areDropped() {
        Jwt jwt = jwtWithClaims(Map.of("permissions", List.of("trader.read", "", "   ")));

        List<String> authorities = converter.convert(jwt).stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        assertThat(authorities).containsExactly("PERM_trader.read");
    }

    @Test
    @DisplayName("duplicate permissions collapse to one authority")
    void duplicatePermissions_collapseToOne() {
        Jwt jwt = jwtWithClaims(Map.of("permissions", List.of("trader.read", "trader.read")));

        assertThat(converter.convert(jwt)).hasSize(1);
    }
}
