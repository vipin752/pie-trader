package com.pietrader.broker.angel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.net.URI;
import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Angel One Smart Stream WebSocket Client.
 *
 * Angel sends ticks as BINARY frames (binary mode 3 = snap quote).
 * The onMessage(String) override never fires for binary data.
 * We must override onMessage(ByteBuffer) and parse the byte array.
 *
 * Angel Binary Packet Layout (mode 3 — snap quote):
 * Offset  Size  Field
 *   0      1    subscription type
 *   1      25   token (padded string)
 *   26     2    sequence number
 *   28     8    exchange timestamp (epoch ms)
 *   36     8    LTP           (divide by 100)
 *   44     8    LTQ
 *   52     8    avg trade price
 *   60     8    volume
 *   68     8    total buy qty
 *   76     8    total sell qty
 *   84     8    open          (divide by 100)
 *   92     8    high          (divide by 100)
 *  100     8    low           (divide by 100)
 *  108     8    close price   (divide by 100)
 *  116     8    best bid price (divide by 100)
 *  124     4    best bid qty
 *  128     8    best ask price (divide by 100)
 *  136     4    best ask qty
 *  140     8    OI
 *  148     8    OI day high
 *  156     8    OI day low
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AngelWebSocketClient {

    private final AngelSessionManager   sessionManager;
    private final AngelTickPublisher    tickPublisher;
    private final AngelTokenService     tokenService;

    private WebSocketClient     client;
    private final ObjectMapper  mapper = new ObjectMapper();

    private static final String WS_URL = "wss://smartapisocket.angelone.in/smart-stream";

    // ── CONNECT ──────────────────────────────────────────────────────────────
    public void connect(List<String> tokens) {
        try {
            String feedToken = sessionManager.getFeedToken();
            String clientId  = sessionManager.getClientId();

            // Angel requires token + clientCode in WS URL params
            String url = WS_URL + "?token=" + feedToken + "&clientCode=" + clientId
                    + "&apiKey=" + sessionManager.getApiKey();

            client = new WebSocketClient(new URI(url)) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("📡 Angel WebSocket connected");
                    subscribe(tokens);
                }

                // ── TEXT MESSAGE (heartbeat/control) ─────────────────────────
                @Override
                public void onMessage(String message) {
                    log.debug("WS text: {}", message);
                }

                // ── BINARY TICK DATA ─────────────────────────────────────────
                @Override
                public void onMessage(ByteBuffer bytes) {
                    try {
                        Map<String, Object> tick = parseBinaryTick(bytes.array());
                        if (tick != null) {
                            tickPublisher.publish(mapper.writeValueAsString(tick));
                        }
                    } catch (Exception e) {
                        log.error("❌ Binary tick parse error", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("⚠️ WS closed (code={} reason={}) — reconnecting in 5s", code, reason);
                    scheduleReconnect(tokens);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("❌ WS error: {}", ex.getMessage());
                }
            };

            // ── SSL bypass for java-websocket library ─────────────────────
            // java-websocket does NOT pick up SSLContext.setDefault() or
            // HttpsURLConnection.setDefaultSSLSocketFactory().
            // Must set trust-all socket factory DIRECTLY on the client instance.
            try {
                javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                        new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAll, new java.security.SecureRandom());
                client.setSocketFactory(sc.getSocketFactory());
                log.debug("🔓 WS SSL bypass applied");
            } catch (Exception sslEx) {
                log.warn("⚠️ Could not apply WS SSL bypass: {}", sslEx.getMessage());
            }

            client.connectBlocking();

        } catch (Exception e) {
            log.error("❌ WS connect error", e);
        }
    }

    // ── SUBSCRIBE ─────────────────────────────────────────────────────────────
    private void subscribe(List<String> tokens) {
        try {
            SubscribeRequest req = new SubscribeRequest(
                    sessionManager.getFeedToken(),
                    tokens
            );
            client.send(mapper.writeValueAsString(req));
            log.info("📡 Subscribed {} tokens", tokens.size());
        } catch (Exception e) {
            log.error("❌ Subscribe error", e);
        }
    }

    // ── BINARY PARSER ─────────────────────────────────────────────────────────
    private Map<String, Object> parseBinaryTick(byte[] data) {
        if (data == null || data.length < 60) return null;

        try {
            ByteBuffer buf = ByteBuffer.wrap(data);

            // Offset 0: subscription type (1 byte)
            byte subType = buf.get(0);

            // Offset 1–25: token (25 bytes, null-padded string)
            byte[] tokenBytes = new byte[25];
            buf.position(1);
            buf.get(tokenBytes);
            String token = new String(tokenBytes).trim().replaceAll("\u0000", "");

            // Lookup token info
            var info = tokenService.getTokenInfo(token);
            String symbol     = info != null ? info.getName()        : "UNKNOWN";
            String optionType = info != null ? info.getOptionType()  : "";
            int    strike     = info != null ? info.getStrikePrice() : 0;

            // Offset 36: LTP (8 bytes long, divide by 100)
            long ltpRaw = getLong(data, 36);
            double ltp  = ltpRaw / 100.0;

            // Offset 60: volume (8 bytes)
            long volume = getLong(data, 60);

            // Offset 84: open
            double open  = getLong(data, 84)  / 100.0;
            double high  = getLong(data, 92)  / 100.0;
            double low   = getLong(data, 100) / 100.0;
            double close = getLong(data, 108) / 100.0;

            // Offset 116: best bid price
            double bid = getLong(data, 116) / 100.0;
            // Offset 128: best ask price
            double ask = getLong(data, 128) / 100.0;

            // Offset 140: OI
            long oi = data.length > 148 ? getLong(data, 140) : 0L;

            // Offset 28: exchange timestamp (8 bytes epoch ms)
            long timestamp = getLong(data, 28);

            // Map.of() supports max 10 entries — use HashMap for 14 fields
            Map<String, Object> tick = new java.util.HashMap<>();
            tick.put("token",      token);
            tick.put("symbol",     symbol);
            tick.put("strike",     strike);
            tick.put("optionType", optionType);
            tick.put("ltp",        ltp);
            tick.put("volume",     volume);
            tick.put("oi",         oi);
            tick.put("bid",        bid);
            tick.put("ask",        ask);
            tick.put("open",       open);
            tick.put("high",       high);
            tick.put("low",        low);
            tick.put("close",      close);
            tick.put("timestamp",  timestamp > 0 ? timestamp : System.currentTimeMillis());
            return tick;

        } catch (Exception e) {
            log.error("Binary parse failed: {}", e.getMessage());
            return null;
        }
    }

    private long getLong(byte[] data, int offset) {
        if (offset + 8 > data.length) return 0L;
        long val = 0;
        for (int i = 0; i < 8; i++) {
            val = (val << 8) | (data[offset + i] & 0xFF);
        }
        return val;
    }

    // ── RECONNECT ────────────────────────────────────────────────────────────
    private void scheduleReconnect(List<String> tokens) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(5000);
                log.info("🔄 Reconnecting WebSocket...");
                connect(tokens);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }
}
