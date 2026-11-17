package com.pietrader.config;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PIE TRADER — SecurityConfigTest (Keycloak JWT integration)
 *
 * Proves SecurityConfig's authorization matrix against every real route
 * this gateway exposes (enumerated directly from each controller's own
 * @GetMapping/@PostMapping — not assumed). Routes are mirrored 1:1 by
 * StubRoutesController below rather than invoking the real controllers,
 * so these tests never touch Postgres, Redis, Kafka, Angel One, or any
 * shared mutable trading state (ModeManager, TradeStateManager, ...) —
 * only the URL + method -> required-authority rules in SecurityConfig
 * itself are under test here. See PermissionsAuthoritiesConverterTest for
 * the claims->authorities mapping and JwtDecoderValidationTest for real
 * signature/issuer/audience/expiry validation.
 *
 * jwt().authorities(...) sets granted authorities directly, bypassing
 * PermissionsAuthoritiesConverter entirely (that's covered separately) —
 * this file exists purely to test SecurityConfig's requestMatchers rules.
 */
@WebMvcTest(controllers = SecurityConfigTest.StubRoutesController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SecurityConfig — Keycloak JWT authorization matrix (real routes)")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // A real JwtDecoder bean must exist for .oauth2ResourceServer().jwt() to
    // build at all; jwt() test support bypasses it for every test below,
    // so a decoder that never actually resolves anything is sufficient.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Mirrors every real controller's path+method 1:1 — see class javadoc. */
    @RestController
    static class StubRoutesController {

        @GetMapping("/") ResponseEntity<String> root() { return ResponseEntity.ok("ok"); }
        @GetMapping("/health") ResponseEntity<String> health() { return ResponseEntity.ok("ok"); }

        @GetMapping("/test/redis/set") ResponseEntity<String> redisSet() { return ResponseEntity.ok("ok"); }
        @GetMapping("/test/redis/get") ResponseEntity<String> redisGet() { return ResponseEntity.ok("ok"); }
        @GetMapping("/test/redis/exists") ResponseEntity<String> redisExists() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/broker/angel/redirect") ResponseEntity<String> angelRedirect() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/broker/status") ResponseEntity<String> brokerStatus() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/broker/login") ResponseEntity<String> brokerLogin() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/broker/test/ltp") ResponseEntity<String> brokerTestLtp() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/broker/test/order") ResponseEntity<String> brokerTestOrder() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/dashboard") ResponseEntity<String> dashboard() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/trade-card") ResponseEntity<String> tradeCard() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/positions") ResponseEntity<String> positions() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/risk") ResponseEntity<String> risk() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/journal") ResponseEntity<String> journal() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/stats") ResponseEntity<String> stats() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/stats/today") ResponseEntity<String> statsToday() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/mode") ResponseEntity<String> mode() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/manual-trade") ResponseEntity<String> manualTrade() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/squareoff") ResponseEntity<String> squareoff() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/options/summary") ResponseEntity<String> optionsSummary() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/options/trigger") ResponseEntity<String> optionsTrigger() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/options/dashboard/{symbol}") ResponseEntity<String> optionsDashboard(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/options/python/health") ResponseEntity<String> optionsPythonHealth() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/options/test-kafka/{symbol}") ResponseEntity<String> optionsTestKafka(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }

        @PostMapping("/api/execution/force") ResponseEntity<String> executionForce() { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/execution/squareoff") ResponseEntity<String> executionSquareoff() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/execution/position/{symbol}") ResponseEntity<String> executionPosition(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/execution/risk/{symbol}") ResponseEntity<String> executionRisk(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/execution/unlock/{symbol}") ResponseEntity<String> executionUnlock(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @PostMapping("/api/execution/reset/{symbol}") ResponseEntity<String> executionReset(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/system/health") ResponseEntity<String> systemHealth() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/system/health/broker") ResponseEntity<String> systemHealthBroker() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/system/health/execution-gate") ResponseEntity<String> systemHealthExecutionGate() { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/system/pipeline") ResponseEntity<String> systemPipeline() { return ResponseEntity.ok("ok"); }

        @GetMapping("/api/signals/latest/{symbol}") ResponseEntity<String> signalsLatest(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/signals/history/{symbol}") ResponseEntity<String> signalsHistory(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/signals/live/{symbol}") ResponseEntity<String> signalsLive(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/signals/cached/{symbol}") ResponseEntity<String> signalsCached(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
        @GetMapping("/api/signals/summary/{symbol}") ResponseEntity<String> signalsSummary(@PathVariable String symbol) { return ResponseEntity.ok("ok"); }
    }

    /** (method, path, the ONE authority that must grant access) */
    static Stream<Arguments> protectedRoutes() {
        return Stream.of(
            // read
            Arguments.of(HttpMethod.GET, "/api/dashboard", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/trade-card", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/positions", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/risk", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/journal", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/stats", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/stats/today", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/options/summary", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/options/dashboard/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/options/python/health", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/execution/position/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/execution/risk/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/signals/latest/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/signals/history/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/signals/live/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/signals/cached/NIFTY", "PERM_trader.read"),
            Arguments.of(HttpMethod.GET, "/api/signals/summary/NIFTY", "PERM_trader.read"),

            // execute
            Arguments.of(HttpMethod.POST, "/api/options/trigger", "PERM_trader.execute"),

            // manual
            Arguments.of(HttpMethod.POST, "/api/execution/force", "PERM_trader.manual"),
            Arguments.of(HttpMethod.POST, "/api/manual-trade", "PERM_trader.manual"),

            // squareoff
            Arguments.of(HttpMethod.POST, "/api/execution/squareoff", "PERM_trader.squareoff"),
            Arguments.of(HttpMethod.POST, "/api/squareoff", "PERM_trader.squareoff"),

            // admin
            Arguments.of(HttpMethod.POST, "/api/execution/unlock/NIFTY", "PERM_trader.admin"),
            Arguments.of(HttpMethod.POST, "/api/execution/reset/NIFTY", "PERM_trader.admin"),
            Arguments.of(HttpMethod.POST, "/api/mode", "PERM_trader.admin"),
            Arguments.of(HttpMethod.POST, "/api/broker/login", "PERM_trader.admin"),

            // ops
            Arguments.of(HttpMethod.GET, "/api/system/health", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/system/health/broker", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/system/health/execution-gate", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/system/pipeline", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/options/test-kafka/NIFTY", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/broker/status", "PERM_trader.ops"),
            Arguments.of(HttpMethod.GET, "/api/broker/test/ltp", "PERM_trader.ops"),
            Arguments.of(HttpMethod.POST, "/api/broker/test/order", "PERM_trader.ops")
        );
    }

    @ParameterizedTest(name = "{1} with no token -> 401")
    @MethodSource("protectedRoutes")
    void protectedRoute_noToken_isUnauthorized(HttpMethod method, String path, String requiredAuthority) throws Exception {
        mockMvc.perform(request(method, path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{1} with its required authority -> not 401/403")
    @MethodSource("protectedRoutes")
    void protectedRoute_correctAuthority_isNotBlocked(HttpMethod method, String path, String requiredAuthority) throws Exception {
        var result = mockMvc.perform(
            request(method, path).with(jwt().authorities(new SimpleGrantedAuthority(requiredAuthority)))
        ).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{1} with an unrelated authority -> 403")
    @MethodSource("protectedRoutes")
    void protectedRoute_wrongAuthority_isForbidden(HttpMethod method, String path, String requiredAuthority) throws Exception {
        String unrelated = "PERM_trader.read".equals(requiredAuthority) ? "PERM_trader.ops" : "PERM_trader.read";

        mockMvc.perform(request(method, path).with(jwt().authorities(new SimpleGrantedAuthority(unrelated))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("public routes need no token at all")
    void publicRoutes_needNoToken() throws Exception {
        // Asserting "not 401/403" (not isOk()) on purpose: in this slice,
        // Spring Boot's default static-resource handling sometimes
        // intercepts a plain top-level GET ahead of the stub controller
        // (a test-harness quirk of this slice's handler-mapping order, not
        // something SecurityConfig itself does differently per path) — a
        // resulting 404 still proves Security let the request through
        // un-challenged, which is exactly what permitAll means.
        assertNotBlockedBySecurity(mockMvc.perform(request(HttpMethod.GET, "/health")).andReturn());
        assertNotBlockedBySecurity(mockMvc.perform(request(HttpMethod.GET, "/api/broker/angel/redirect")).andReturn());
    }

    private static void assertNotBlockedBySecurity(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("Redis test endpoints are denied even with every known authority")
    void redisTestEndpoints_areAlwaysDenied() throws Exception {
        var allAuthorities = jwt().authorities(
            new SimpleGrantedAuthority("PERM_trader.read"),
            new SimpleGrantedAuthority("PERM_trader.execute"),
            new SimpleGrantedAuthority("PERM_trader.manual"),
            new SimpleGrantedAuthority("PERM_trader.squareoff"),
            new SimpleGrantedAuthority("PERM_trader.ops"),
            new SimpleGrantedAuthority("PERM_trader.admin")
        );

        mockMvc.perform(request(HttpMethod.GET, "/test/redis/set").with(allAuthorities)).andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.GET, "/test/redis/get").with(allAuthorities)).andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.GET, "/test/redis/exists").with(allAuthorities)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a valid token with no permissions at all is forbidden everywhere except public routes")
    void authenticatedWithNoPermissions_isForbiddenOnProtectedRoutes() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/dashboard").with(jwt()))
            .andExpect(status().isForbidden());
    }
}
