package com.pietrader.broker.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.broker.BrokerAdapter;
import com.pietrader.broker.angel.AngelAuthService;
import com.pietrader.broker.angel.AngelSymbolMapper;
import com.pietrader.broker.model.ActivePosition;
import com.pietrader.broker.model.OrderRequest;
import com.pietrader.broker.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * PHASE 4 — Angel One Broker Adapter
 * Implements BrokerAdapter — swap with Zerodha/Dhan without touching ExecutionEngine.
 */
@Service
@Slf4j
public class AngelBrokerAdapter implements BrokerAdapter {

    private final AngelAuthService  authService;
    private final AngelSymbolMapper symbolMapper;
    private final RestTemplate      restTemplate;
    private final ObjectMapper      objectMapper = new ObjectMapper();

    @Value("${angel.api.key}")   private String apiKey;
    @Value("${angel.base.url}")  private String baseUrl;

    public AngelBrokerAdapter(AngelAuthService authService,
                              AngelSymbolMapper symbolMapper,
                              @Qualifier("angelRestTemplate") RestTemplate restTemplate) {
        this.authService  = authService;
        this.symbolMapper = symbolMapper;
        this.restTemplate = restTemplate;
    }

    // ── PLACE ORDER ─────────────────────────────────────────────────────────
    @Override
    public OrderResponse placeOrder(OrderRequest req) {
        try {
            log.info("📡 placeOrder → {} {} {}", req.getSymbol(), req.getStrike(), req.getDirection());

            String jwtToken = authService.getToken();

            // parse "23000 PE" into strikePrice + optionType
            String[] parts = req.getStrike().split(" ");
            if (parts.length < 2) throw new RuntimeException("Invalid strike: " + req.getStrike());
            String strikePrice = parts[0];
            String optionType  = parts[1];

            Map<String, String> instrument = symbolMapper.findOption(req.getSymbol(), strikePrice, optionType);
            if (instrument == null) throw new RuntimeException("Symbol not found: " + req.getStrike());

            String tradingSymbol = instrument.get("tradingsymbol");
            String symbolToken   = instrument.get("symboltoken");
            log.info("✅ Mapped → {} | token={}", tradingSymbol, symbolToken);

            HttpHeaders headers = buildHeaders(jwtToken);
            Map<String, Object> body = new HashMap<>();
            body.put("variety",         "NORMAL");
            body.put("tradingsymbol",   tradingSymbol);
            body.put("symboltoken",     symbolToken);
            body.put("transactiontype", "UP".equalsIgnoreCase(req.getDirection()) ? "BUY" : "SELL");
            body.put("exchange",        "NFO");
            body.put("ordertype",       req.getOrderType() != null ? req.getOrderType() : "MARKET");
            body.put("producttype",     req.getProductType() != null ? req.getProductType() : "INTRADAY");
            body.put("duration",        "DAY");
            body.put("quantity",        req.getQuantity());
            if (req.getLimitPrice() != null) body.put("price", req.getLimitPrice());

            ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/rest/secure/angelbroking/order/v1/placeOrder",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> rb = resp.getBody();
            log.info("📊 Angel Response: {}", rb);

            if (rb != null && "success".equalsIgnoreCase((String) rb.get("status"))) {
                Map data = (Map) rb.get("data");
                return OrderResponse.builder()
                    .status("SUCCESS")
                    .orderId(String.valueOf(data.get("orderid")))
                    .symbol(req.getSymbol())
                    .strike(req.getStrike())
                    .direction(req.getDirection())
                    .price(data.get("price") != null ? Double.parseDouble(String.valueOf(data.get("price"))) : 0.0)
                    .quantity(req.getQuantity())
                    .timestamp(System.currentTimeMillis())
                    .build();
            }
            return OrderResponse.builder().status("FAILED").errorMessage(String.valueOf(rb)).timestamp(System.currentTimeMillis()).build();

        } catch (Exception e) {
            log.error("❌ placeOrder failed", e);
            return OrderResponse.builder().status("FAILED").errorMessage(e.getMessage()).timestamp(System.currentTimeMillis()).build();
        }
    }

    // ── CANCEL ORDER ────────────────────────────────────────────────────────
    @Override
    public boolean cancelOrder(String orderId) {
        try {
            HttpHeaders headers = buildHeaders(authService.getToken());
            Map<String, String> body = Map.of("variety", "NORMAL", "orderid", orderId);
            restTemplate.exchange(
                baseUrl + "/rest/secure/angelbroking/order/v1/cancelOrder",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            log.info("✅ Order cancelled: {}", orderId);
            return true;
        } catch (Exception e) {
            log.error("❌ Cancel failed for {}", orderId, e);
            return false;
        }
    }

    // ── GET POSITION ────────────────────────────────────────────────────────
    @Override
    public ActivePosition getPosition(String symbol) {
        List<ActivePosition> all = getAllPositions();
        return all.stream()
            .filter(p -> symbol.equalsIgnoreCase(p.getSymbol()))
            .findFirst().orElse(null);
    }

    @Override
    public List<ActivePosition> getAllPositions() {
        try {
            HttpHeaders headers = buildHeaders(authService.getToken());
            ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/rest/secure/angelbroking/order/v1/getPosition",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> rb = resp.getBody();
            if (rb == null || !"success".equalsIgnoreCase((String) rb.get("status"))) return List.of();

            List<Map<String, Object>> data = (List<Map<String, Object>>) rb.get("data");
            List<ActivePosition> positions = new ArrayList<>();
            if (data != null) {
                for (Map<String, Object> p : data) {
                    positions.add(ActivePosition.builder()
                        .symbol(String.valueOf(p.getOrDefault("symbolname", "")))
                        .strike(String.valueOf(p.getOrDefault("tradingsymbol", "")))
                        .quantity(Integer.parseInt(String.valueOf(p.getOrDefault("netqty", "0"))))
                        .entryPrice(Double.parseDouble(String.valueOf(p.getOrDefault("avgnetprice", "0"))))
                        .unrealisedPnl(Double.parseDouble(String.valueOf(p.getOrDefault("unrealised", "0"))))
                        .build());
                }
            }
            return positions;
        } catch (Exception e) {
            log.error("❌ getPositions failed", e);
            return List.of();
        }
    }

    // ── SQUARE OFF ──────────────────────────────────────────────────────────
    @Override
    public OrderResponse squareOff(String symbol, String strike, int quantity) {
        return placeOrder(OrderRequest.builder()
            .symbol(symbol).strike(strike)
            .direction("DOWN").quantity(quantity)
            .orderType("MARKET").productType("INTRADAY")
            .build());
    }

    private HttpHeaders buildHeaders(String jwtToken) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + jwtToken);
        h.set("X-PrivateKey", apiKey);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
