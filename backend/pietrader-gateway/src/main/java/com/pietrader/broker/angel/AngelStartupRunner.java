package com.pietrader.broker.angel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PIE TRADER — AngelStartupRunner
 *
 * Does NOT block on stdin.
 * TOTP is submitted via REST after app starts:
 *   POST /api/broker/login  {"totp":"123456"}
 *
 * FIX: After building option tokens, we now also add the spot/index token
 * for each watched symbol via tokenService.getSpotToken(symbol).
 * Without subscribing token 26000 (NIFTY), Angel SmartStream never sends
 * the spot price tick → Python spot stays 0.0 → "No option chain data".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AngelStartupRunner implements CommandLineRunner {

    private final AngelSessionManager  sessionManager;
    private final AngelTokenService    tokenService;
    private final AngelWebSocketClient webSocketClient;
    private final AngelRestClient      restClient;

    @Value("${angel.startup.auto:false}")
    private boolean autoStart;

    private static final String[] WATCH_SYMBOLS = {"NIFTY", "BANKNIFTY"};
    private static final int      ATM_RANGE     = 500;

    @Override
    public void run(String... args) {
        log.info("🚀 PIE Trader started.");
        String totpSecret = sessionManager.getTotpSecret();
        if (autoStart && totpSecret != null && !totpSecret.isBlank() && !"NA".equals(totpSecret)) {
            log.info("🔑 Auto-login with configured TOTP secret...");
            try { connectWithTotp(generateTotp(totpSecret)); }
            catch (Exception e) { log.warn("⚠️ Auto-login failed: {} — POST /api/broker/login", e.getMessage()); }
        } else {
            log.info("⏳ Waiting for TOTP. POST http://localhost:8080/api/broker/login  Body: {\"totp\":\"123456\"}");
        }
    }

    /** Called by AngelLoginController when TOTP submitted via REST */
    public void connectWithTotp(String totp) {
        log.info("🔐 Logging in (clientId={})...", sessionManager.getClientId());
        sessionManager.login(totp);
        if (sessionManager.getFeedToken() == null)
            throw new RuntimeException("Login failed — feedToken is null. Check credentials.");
        log.info("✅ Login OK");
        sessionManager.startSessionMaintenance();

        List<String> allTokens = new ArrayList<>();

        for (String symbol : WATCH_SYMBOLS) {
            int atm = getAtm(symbol);
            log.info("📊 {} ATM={}", symbol, atm);

            // Option tokens (unchanged)
            List<String> optionTokens = tokenService.getTokensForStrikeRange(symbol, atm, ATM_RANGE);
            log.info("   {} option tokens for {}", optionTokens.size(), symbol);
            allTokens.addAll(optionTokens);

            // FIX: Add spot/index token so Angel sends spot price ticks
            // Without this, token 26000 (NIFTY) is never subscribed → no spot ticks →
            // Python LiveOptionChain.get_spot("NIFTY") always returns 0.0
            String spotToken = tokenService.getSpotToken(symbol);
            if (spotToken != null) {
                allTokens.add(spotToken);
                log.info("   📍 Spot token subscribed: {} → {}", symbol, spotToken);
            } else {
                log.warn("   ⚠️ No spot token for {} — verify OpenAPIScripMaster.json loaded", symbol);
            }
        }

        if (allTokens.isEmpty()) {
            log.warn("⚠️ No tokens found — check angel/OpenAPIScripMaster.json in resources");
            return;
        }

        webSocketClient.connect(allTokens);
        log.info("✅ WebSocket LIVE — {} tokens subscribed → Kafka pie.market.ticks", allTokens.size());
    }

    private int getAtm(String symbol) {
        try {
            String ts = switch (symbol) { case "BANKNIFTY" -> "Nifty Bank"; case "FINNIFTY" -> "Nifty Fin Service"; default -> "Nifty 50"; };
            String st = switch (symbol) { case "BANKNIFTY" -> "26009"; case "FINNIFTY" -> "26037"; default -> "26000"; };
            var node = restClient.post("/rest/secure/angelbroking/order/v1/getLtpData",
                    String.format("{\"exchange\":\"NSE\",\"tradingsymbol\":\"%s\",\"symboltoken\":\"%s\"}", ts, st));
            if (node != null && node.path("status").asBoolean()) {
                double ltp = node.path("data").path("ltp").asDouble();
                int gap = "BANKNIFTY".equals(symbol) ? 100 : 50;
                return (int)(Math.round(ltp / gap) * gap);
            }
        } catch (Exception e) { log.warn("⚠️ LTP fetch failed {}: {}", symbol, e.getMessage()); }
        return "BANKNIFTY".equals(symbol) ? 52000 : 23000;
    }

    private String generateTotp(String secret) throws Exception {
        long c = System.currentTimeMillis() / 1000 / 30;
        byte[] key = base32Decode(secret.toUpperCase().replaceAll("[^A-Z2-7]", ""));
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) { msg[i] = (byte)(c & 0xFF); c >>= 8; }
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
        byte[] h = mac.doFinal(msg);
        int off = h[h.length-1] & 0x0F;
        int code = ((h[off]&0x7F)<<24)|((h[off+1]&0xFF)<<16)|((h[off+2]&0xFF)<<8)|(h[off+3]&0xFF);
        return String.format("%06d", code % 1_000_000);
    }

    private byte[] base32Decode(String s) {
        final String A = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] out = new byte[s.length() * 5 / 8]; int buf=0,bits=0,idx=0;
        for (char ch : s.toCharArray()) { int p=A.indexOf(ch); if(p<0) continue; buf=(buf<<5)|p; bits+=5; if(bits>=8) out[idx++]=(byte)(buf>>(bits-=8)); }
        return out;
    }
}
