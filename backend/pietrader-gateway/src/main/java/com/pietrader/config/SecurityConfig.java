package com.pietrader.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Keycloak-backed JWT authorization for every PIE Trader gateway route.
 *
 * Replaces the previous {@code anyRequest().permitAll()} placeholder (left
 * here with a "tighten with JWT" TODO since the gateway's Phase 1 security
 * pass) with a real, route-by-route matrix built directly from this
 * module's actual controllers (see each controller's own mappings — this
 * list is not assumed from the Keycloak client's permission names alone).
 *
 * Authority names are "PERM_" + the Keycloak client-role name carried in
 * the access token's {@code permissions} claim (mapped by
 * {@link PermissionsAuthoritiesConverter}), e.g. {@code PERM_trader.read}.
 *
 * SECURITY NOTE (flagged, not fixed here — out of scope for an auth-only
 * change): {@code POST /api/mode} can set trading mode to {@code LIVE},
 * which directly concerns the master prompt's non-negotiable "never enable
 * live trading" rule. Gating it behind {@code PERM_trader.admin} is a real
 * mitigation, but it does not make LIVE mode itself safe to allow — that
 * needs its own explicit Risk Authority Review (Phase 2), not an
 * authentication change.
 *
 * SECURITY NOTE (flagged, not fixed here): {@code GET
 * /api/options/test-kafka/{symbol}} is a GET request that triggers a real
 * Python-analytics-run + Kafka-publish + AnalyticsConsumer execution cycle
 * (see its own class javadoc) — the same category of issue CR-002 fixed
 * for {@code /api/options/summary}. Gating it behind {@code PERM_trader.ops}
 * closes the practical exposure for now; it should still become a POST
 * command like {@code /api/options/trigger} in a future change.
 */
@Configuration
public class SecurityConfig {

    @Value("${pie.trader.security.allowed-origin}")
    private String allowedOrigin;

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new PermissionsAuthoritiesConverter());
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Stateless bearer-token API — revisit if auth ever moves to cookies/sessions.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Public ────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/", "/health", "/actuator/health").permitAll()
                .requestMatchers(
                    "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**"
                ).permitAll()
                // Angel One's own browser redirect after their OAuth login page —
                // reached directly by the user's browser, never carries our Bearer token.
                .requestMatchers(HttpMethod.GET, "/api/broker/angel/redirect").permitAll()

                // ── Never expose ──────────────────────────────────────────
                .requestMatchers("/test/redis/**").denyAll()

                // ── Operations / diagnostics ──────────────────────────────
                .requestMatchers(
                    "/api/system/**", "/actuator/**"
                ).hasAuthority("PERM_trader.ops")
                .requestMatchers(HttpMethod.GET, "/api/options/test-kafka/{symbol}").hasAuthority("PERM_trader.ops")
                .requestMatchers(HttpMethod.GET, "/api/broker/status", "/api/broker/test/ltp").hasAuthority("PERM_trader.ops")
                .requestMatchers(HttpMethod.POST, "/api/broker/test/order").hasAuthority("PERM_trader.ops")

                // ── Option analytics execution trigger (CR-002 command) ──
                .requestMatchers(HttpMethod.POST, "/api/options/trigger").hasAuthority("PERM_trader.execute")

                // ── Manual trade execution ────────────────────────────────
                .requestMatchers(HttpMethod.POST,
                    "/api/execution/force", "/api/manual-trade"
                ).hasAuthority("PERM_trader.manual")

                // ── Square-off ─────────────────────────────────────────────
                .requestMatchers(HttpMethod.POST,
                    "/api/execution/squareoff", "/api/squareoff"
                ).hasAuthority("PERM_trader.squareoff")

                // ── Administrative (broker login, emergency unlock/reset, mode) ─
                .requestMatchers(HttpMethod.POST,
                    "/api/execution/unlock/{symbol}", "/api/execution/reset/{symbol}",
                    "/api/mode", "/api/broker/login"
                ).hasAuthority("PERM_trader.admin")

                // ── Read-only ──────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET,
                    "/api/dashboard", "/api/trade-card", "/api/positions", "/api/risk",
                    "/api/journal", "/api/stats", "/api/stats/today",
                    "/api/options/summary", "/api/options/dashboard/{symbol}", "/api/options/python/health",
                    "/api/execution/position/{symbol}", "/api/execution/risk/{symbol}",
                    "/api/signals/**"
                ).hasAuthority("PERM_trader.read")

                // No accidental public/authenticated-but-unlisted routes.
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
