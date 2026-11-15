package com.pietrader.broker.angel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.config.SslConfig;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Loads OpenAPIScripMaster.json from classpath first, live URL fallback.
 *
 * ROOT CAUSE FIX:
 * NSE spot tokens in OpenAPIScripMaster.json have instrumenttype="" (empty), NOT "INDEX":
 *   token=26000  → NIFTY      instrumenttype=""      exch_seg=NSE
 *   token=26009  → BANKNIFTY  instrumenttype=""      exch_seg=NSE
 *   token=26037  → FINNIFTY   instrumenttype=""      exch_seg=NSE
 *   token=26074  → MIDCPNIFTY instrumenttype=""      exch_seg=NSE
 * (AMXIDX variants 99926000/99926009 also exist but shorter tokens are preferred)
 *
 * Old code: if (!OPTIDX) continue → these tokens were NEVER loaded into tokenMap.
 * Result: getTokenInfo("26000") = null → WebSocket tick decoded as symbol="UNKNOWN",
 *         optionType="" → Python LiveOptionChain.get_spot("NIFTY") = 0.0 forever →
 *         AngelOptionChainAdapter returns null → {"error":"No option chain data"}.
 */
@Service
@Slf4j
public class AngelTokenService {

    private static final Set<String> SUPPORTED_SPOT_NAMES = Set.of(
            "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY"
    );

    // instrumenttype values that identify spot/index feed tokens (NOT options, NOT futures)
    private static final Set<String> SPOT_INSTRUMENT_TYPES = Set.of(
            "",        // 26000-series: empty instrumenttype
            "AMXIDX"   // 99926000-series: alternate spot token format
    );

    @Value("${angel.contract.master:https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json}")
    private String contractMasterUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    @Getter
    private final Map<String, AngelInstrument>       tokenMap  = new ConcurrentHashMap<>();
    private final Map<String, List<AngelInstrument>> symbolMap = new ConcurrentHashMap<>();

    // "NIFTY" → "26000",  "BANKNIFTY" → "26009", etc.
    private final Map<String, String> spotTokenMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadMasterContract() {
        List<Map<String, Object>> data = loadFromClasspath();
        if (data == null || data.isEmpty()) {
            log.warn("Classpath contract missing — downloading live");
            data = loadFromUrl();
        }
        if (data == null || data.isEmpty()) {
            log.error("Failed to load contract from any source"); return;
        }
        parseAndIndex(data);
        log.info("Angel contract loaded: {} option tokens | spot tokens: {}",
                tokenMap.size() - spotTokenMap.size(), spotTokenMap);
    }

    private List<Map<String, Object>> loadFromClasspath() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("angel/OpenAPIScripMaster.json");
            if (is == null) return null;
            List<Map<String, Object>> d = mapper.readValue(is, new TypeReference<>(){});
            log.info("Contract loaded from classpath: {} records", d.size());
            return d;
        } catch (Exception e) { log.warn("Classpath load failed: {}", e.getMessage()); return null; }
    }

    public List<Map<String, Object>> loadFromUrl() {
        try {
            SslConfig.applyGlobalSslBypass();
            log.info("Downloading contract from: {}", contractMasterUrl);
            List<Map<String, Object>> d = mapper.readValue(new URL(contractMasterUrl).openStream(), new TypeReference<>(){});
            log.info("Live contract downloaded: {} records", d.size());
            return d;
        } catch (Exception e) { log.error("URL contract load failed: {}", e.getMessage()); return null; }
    }

    private void parseAndIndex(List<Map<String, Object>> data) {
        tokenMap.clear();
        symbolMap.clear();
        spotTokenMap.clear();

        for (Map<String, Object> item : data) {
            String instrument = Optional.ofNullable((String) item.get("instrumenttype")).orElse("");
            String exchSeg    = Optional.ofNullable((String) item.get("exch_seg")).orElse("");
            String token      = (String) item.get("token");
            if (token == null || token.isBlank()) continue;

            // ── OPTION TOKENS (NFO OPTIDX) — unchanged, zero regression ─────
            if ("OPTIDX".equalsIgnoreCase(instrument) && "NFO".equalsIgnoreCase(exchSeg)) {
                AngelInstrument inst = new AngelInstrument();
                inst.setToken(token);
                inst.setSymbol((String) item.get("symbol"));
                inst.setName((String) item.get("name"));
                inst.setExpiry((String) item.get("expiry"));
                inst.setStrike((String) item.get("strike"));
                inst.setInstrumentType(instrument);
                inst.setExchSeg(exchSeg);
                try {
                    inst.setStrikePrice(
                            (int)(Double.parseDouble(
                                    Optional.ofNullable(inst.getStrike()).orElse("0")) / 100));
                } catch (Exception e) { continue; }
                String sym = inst.getSymbol();
                if (sym != null) {
                    if      (sym.endsWith("CE")) inst.setOptionType("CE");
                    else if (sym.endsWith("PE")) inst.setOptionType("PE");
                }
                tokenMap.put(inst.getToken(), inst);
                symbolMap.computeIfAbsent(
                        Optional.ofNullable(inst.getName()).orElse("").toUpperCase(),
                        k -> new ArrayList<>()
                ).add(inst);
                continue;
            }

            // ── FIX: SPOT TOKENS (NSE, empty or AMXIDX instrumenttype) ───────
            // These tokens deliver index spot price via Angel SmartStream WebSocket.
            // Condition: exch_seg=NSE + name matches a supported symbol
            //            + instrumenttype is "" or "AMXIDX" (NOT options/futures)
            if ("NSE".equalsIgnoreCase(exchSeg) && SPOT_INSTRUMENT_TYPES.contains(instrument)) {
                String name = (String) item.get("name");
                if (name == null) continue;
                String nameUpper = name.toUpperCase();
                if (!SUPPORTED_SPOT_NAMES.contains(nameUpper)) continue;

                AngelInstrument spot = new AngelInstrument();
                spot.setToken(token);
                spot.setName(nameUpper);
                spot.setSymbol(nameUpper);
                spot.setOptionType("SPOT");    // Python LiveOptionChain checks optionType=="SPOT"
                spot.setInstrumentType(instrument);
                spot.setExchSeg("NSE");
                spot.setStrikePrice(0);

                // Register in tokenMap so WebSocket tick lookup works
                tokenMap.put(token, spot);

                // Prefer shorter token (26000) over AMXIDX (99926000) — both work,
                // but the short-form is the primary SmartStream subscription token
                String existing = spotTokenMap.get(nameUpper);
                if (existing == null || token.length() <= existing.length()) {
                    spotTokenMap.put(nameUpper, token);
                }
            }
        }

        spotTokenMap.forEach((sym, tok) ->
                log.info("📍 Spot token: {} → {}", sym, tok));
    }

    // ── PUBLIC API — all methods below are UNCHANGED (zero regression) ───────

    public AngelInstrument getTokenInfo(String token) { return tokenMap.get(token); }

    public List<AngelInstrument> getOptions(String symbol) {
        return symbolMap.getOrDefault(symbol.toUpperCase(), Collections.emptyList());
    }

    public String getNearestExpiry(String symbol) {
        return getOptions(symbol).stream()
                .map(AngelInstrument::getExpiry)
                .filter(Objects::nonNull)
                .distinct().sorted().findFirst().orElse(null);
    }

    public List<String> getTokensForStrikeRange(String symbol, int atm, int range) {
        String exp = getNearestExpiry(symbol);
        if (exp == null) return Collections.emptyList();
        return getOptions(symbol).stream()
                .filter(i -> exp.equals(i.getExpiry()))
                .filter(i -> Math.abs(i.getStrikePrice() - atm) <= range)
                .map(AngelInstrument::getToken)
                .collect(Collectors.toList());
    }

    public String findToken(String symbol, String strikeWithType) {
        try {
            String[] p = strikeWithType.trim().split("\\s+");
            int strike = Integer.parseInt(p[0]); String type = p[1].toUpperCase();
            String exp = getNearestExpiry(symbol);
            return getOptions(symbol).stream()
                    .filter(i -> i.getStrikePrice() == strike && type.equals(i.getOptionType())
                            && exp != null && exp.equals(i.getExpiry()))
                    .map(AngelInstrument::getToken).findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    public String findTradingSymbol(String symbol, String strikeWithType) {
        try {
            String[] p = strikeWithType.trim().split("\\s+");
            int strike = Integer.parseInt(p[0]); String type = p[1].toUpperCase();
            String exp = getNearestExpiry(symbol);
            return getOptions(symbol).stream()
                    .filter(i -> i.getStrikePrice() == strike && type.equals(i.getOptionType())
                            && exp != null && exp.equals(i.getExpiry()))
                    .map(AngelInstrument::getSymbol).findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    /** NEW — returns NSE spot token for SmartStream subscription.
     *  "NIFTY" → "26000", "BANKNIFTY" → "26009", etc. */
    public String getSpotToken(String symbol) {
        return spotTokenMap.get(symbol.toUpperCase());
    }
}
