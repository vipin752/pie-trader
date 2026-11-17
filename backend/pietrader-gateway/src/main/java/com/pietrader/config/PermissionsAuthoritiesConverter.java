package com.pietrader.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts Keycloak's {@code permissions} access-token claim (a "User
 * Client Role" protocol mapper on the {@code pie-trader-api} client role,
 * mapped into the {@code pie-trader-web} client's access token) into
 * Spring Security authorities, each prefixed {@code PERM_} so SecurityConfig
 * can gate routes with {@code hasAuthority("PERM_trader.read")} etc.
 *
 * Accepts either the array form Keycloak emits by default
 * ({@code "permissions": ["trader.read", "trader.execute"]}) or, defensively,
 * a single delimited string, in case a realm's mapper is ever configured
 * without "Multivalued" turned on.
 */
public class PermissionsAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM_NAME = "permissions";
    private static final String AUTHORITY_PREFIX = "PERM_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object claim = jwt.getClaims().get(CLAIM_NAME);
        if (claim == null) {
            return Collections.emptySet();
        }

        Set<String> permissions = new LinkedHashSet<>();
        if (claim instanceof Collection<?> values) {
            for (Object value : values) {
                if (value != null) {
                    permissions.add(value.toString().trim());
                }
            }
        } else if (claim instanceof String value) {
            for (String permission : value.split("[,\\s]+")) {
                permissions.add(permission.trim());
            }
        }

        return permissions.stream()
            .filter(permission -> !permission.isEmpty())
            .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + permission))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
