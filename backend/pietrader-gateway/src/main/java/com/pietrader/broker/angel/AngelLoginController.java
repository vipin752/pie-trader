package com.pietrader.broker.angel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PIE TRADER — AngelLoginController
 *
 * AngelStartupRunner is @ConditionalOnProperty — injected as Optional
 * to prevent ApplicationContext startup failure when angel.startup.enabled=false.
 */
@RestController
@RequestMapping("/api/broker")
@Slf4j
public class AngelLoginController {

    private final AngelSessionManager         sessionManager;
    private final AngelRestClient             restClient;
    private final Optional<AngelStartupRunner> startupRunner;

    @Autowired
    public AngelLoginController(AngelSessionManager sessionManager,
                                AngelRestClient restClient,
                                Optional<AngelStartupRunner> startupRunner) {
        this.sessionManager = sessionManager;
        this.restClient     = restClient;
        this.startupRunner  = startupRunner;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        boolean connected = sessionManager.getJwtToken() != null
            && !sessionManager.getJwtToken().isBlank();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("connected",  connected);
        res.put("clientId",   sessionManager.getClientId());
        res.put("feedToken",  connected ? "acquired" : "not_acquired");
        res.put("wsEnabled",  startupRunner.isPresent());
        res.put("message",    connected
            ? "✅ Angel One connected"
            : "❌ Not connected — POST /api/broker/login with your TOTP");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String totp = body.getOrDefault("totp", "").trim();
        if (totp.isEmpty() || totp.length() != 6)
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "error", "TOTP must be exactly 6 digits"));
        try {
            if (startupRunner.isPresent()) {
                startupRunner.get().connectWithTotp(totp);
            } else {
                sessionManager.login(totp);
            }
            return ResponseEntity.ok(Map.of(
                "success",   true,
                "message",   "✅ Login OK — JWT saved to Redis, not needed again until expiry",
                "wsConnected", startupRunner.isPresent()
            ));
        } catch (Exception e) {
            log.error("❌ Login failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/test/ltp")
    public ResponseEntity<Map<String, Object>> testLtp(
            @RequestParam(defaultValue = "Nifty 50") String symbol,
            @RequestParam(defaultValue = "26000")    String token) {
        try {
            String b = String.format(
                "{\"exchange\":\"NSE\",\"tradingsymbol\":\"%s\",\"symboltoken\":\"%s\"}",
                symbol, token);
            var node = restClient.post("/rest/secure/angelbroking/order/v1/getLtpData", b);
            if (node != null && node.path("status").asBoolean())
                return ResponseEntity.ok(Map.of(
                    "success", true, "symbol", symbol,
                    "ltp", node.path("data").path("ltp").asDouble()));
            return ResponseEntity.ok(Map.of(
                "success", false, "raw", node != null ? node.toString() : "null"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/test/order")
    public ResponseEntity<Map<String, Object>> testOrder(
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "note", "trading.mode=PAPER — use POST /api/manual-trade for full flow"));
    }
}
