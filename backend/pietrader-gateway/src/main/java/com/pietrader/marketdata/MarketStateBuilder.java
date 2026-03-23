package com.pietrader.marketdata;

import com.pietrader.broker.angel.AngelTokenService;
import com.pietrader.dto.market.MarketStateDTO;
import com.pietrader.marketdata.kafka.MarketStateKafkaProducer;
import com.pietrader.model.RawOptionChainRecord;
import com.pietrader.model.StrikeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PIE TRADER — MarketStateBuilder
 *
 * GAP-2 FIX: MarketStateKafkaProducer existed but was never called.
 * Result: pie.market.state had 0 messages → Python MarketStateConsumer
 *         received nothing → Python intelligence had no regime/gamma/PCR input.
 *
 * This component:
 *   1. Accumulates tick data from AngelTickPublisher into an internal cache
 *      (keyed by symbol → strike → CE/PE data)
 *   2. Every 2 seconds: computes MarketStateDTO from cached strikes
 *   3. Publishes to Kafka pie.market.state
 *
 * Data flow:
 *   AngelWebSocketClient → AngelTickPublisher → MarketStateBuilder.onTick()
 *                       → MarketStateKafkaProducer → pie.market.state → Python
 *
 * Wire-up: AngelTickPublisher calls this.onTick(tickJson) after Kafka publish.
 * (Add one line to AngelTickPublisher.publish() — see comment there.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketStateBuilder {

    private final MarketStateKafkaProducer producer;
    private final AngelTokenService        tokenService;

    @Value("${trading.market.open:09:20}")  private String marketOpenStr;
    @Value("${trading.market.close:15:20}") private String marketCloseStr;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final List<String> SUPPORTED = List.of("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY");

    // Tick accumulator: symbol → token → tick map
    // Updated by onTick() on every WebSocket tick
    private final Map<String, Map<String, Map<String, Object>>> tickCache =
	    new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // TICK INTAKE (called by AngelTickPublisher after Kafka publish)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by AngelTickPublisher on every published tick.
     * Accumulates ticks in memory for MarketState computation.
     *
     * tickMap contains: token, symbol, strike, optionType, ltp, oi, volume,
     *                   bid, ask, iv, open, high, low, close, timestamp
     */
    public void onTick(Map<String, Object> tickMap) {
	try {
	    String symbol     = String.valueOf(tickMap.getOrDefault("symbol", "")).toUpperCase();
	    String optionType = String.valueOf(tickMap.getOrDefault("optionType", ""));
	    String token      = String.valueOf(tickMap.getOrDefault("token", ""));

	    if (symbol.isEmpty() || token.isEmpty()) return;
	    if (!SUPPORTED.contains(symbol) && !"SPOT".equals(optionType)) return;

	    tickCache
		    .computeIfAbsent(symbol, s -> new ConcurrentHashMap<>())
		    .put(token, tickMap);

	} catch (Exception e) {
	    log.debug("Tick cache error: {}", e.getMessage());
	}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEDULED PUBLISH (every 2 seconds)
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 2000)
    public void buildAndPublish() {
	if (!isMarketHours()) return;

	for (String symbol : SUPPORTED) {
	    try {
		Map<String, Map<String, Object>> symbolTicks = tickCache.get(symbol);
		if (symbolTicks == null || symbolTicks.isEmpty()) continue;

		MarketStateDTO state = buildState(symbol, symbolTicks);
		if (state != null) {
		    producer.publish(state);
		}
	    } catch (Exception e) {
		log.error("❌ MarketStateBuilder error for {}: {}", symbol, e.getMessage());
	    }
	}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATE COMPUTATION
    // ─────────────────────────────────────────────────────────────────────────

    private MarketStateDTO buildState(String symbol, Map<String, Map<String, Object>> ticks) {
	double spot      = 0.0;
	double totalCallOI = 0, totalPutOI = 0;
	double totalCallVol = 0, totalPutVol = 0;
	double atmIv     = 0.0;
	int    atm       = 0;

	// Option chain accumulator: strike → CE/PE data
	Map<Integer, double[]> chain = new TreeMap<>();
	// [0]=callOI [1]=putOI [2]=callVol [3]=putVol [4]=callIV [5]=putIV [6]=callLTP [7]=putLTP

	for (Map<String, Object> tick : ticks.values()) {
	    String optType = String.valueOf(tick.getOrDefault("optionType", ""));
	    double ltp     = toDouble(tick.get("ltp"));
	    double oi      = toDouble(tick.get("oi"));
	    double vol     = toDouble(tick.get("volume"));
	    double iv      = toDouble(tick.get("iv"));
	    int    strike  = toInt(tick.get("strike"));

	    if ("SPOT".equals(optType)) {
		spot = ltp;
		continue;
	    }

	    chain.computeIfAbsent(strike, k -> new double[8]);
	    double[] d = chain.get(strike);

	    if ("CE".equals(optType)) {
		d[0] = oi; d[2] = vol; d[4] = iv; d[6] = ltp;
		totalCallOI  += oi;
		totalCallVol += vol;
	    } else if ("PE".equals(optType)) {
		d[1] = oi; d[3] = vol; d[5] = iv; d[7] = ltp;
		totalPutOI   += oi;
		totalPutVol  += vol;
	    }
	}

	if (spot <= 0 || chain.isEmpty()) return null;

	// ATM
	int strikeGap = symbol.equals("BANKNIFTY") ? 100 : 50;
	atm = (int)(Math.round(spot / strikeGap) * strikeGap);

	// ATM IV
	double[] atmRow = chain.get(atm);
	if (atmRow == null) {
	    // find nearest
	    int finalAtm = atm;
	    atmRow = chain.entrySet().stream()
		    .min(Comparator.comparingInt(e -> Math.abs(e.getKey() - finalAtm)))
		    .map(Map.Entry::getValue).orElse(null);
	}
	if (atmRow != null) {
	    atmIv = (atmRow[4] + atmRow[5]) / 2.0;  // avg call/put IV
	}

	// PCR
	double pcr = totalPutOI > 0 && totalCallOI > 0
		? totalPutOI / totalCallOI : 1.0;

	// Gamma flip: strike where call OI first exceeds put OI going up
	double gammaFlip = computeGammaFlip(chain, atm);

	// Call wall / Put wall
	int finalAtm1 = atm;
	int callWall = chain.entrySet().stream()
		.filter(e -> e.getKey() >= finalAtm1)
		.max(Comparator.comparingDouble(e -> e.getValue()[0]))
		.map(Map.Entry::getKey).orElse(atm + strikeGap * 5);
	int finalAtm2 = atm;
	int putWall = chain.entrySet().stream()
		.filter(e -> e.getKey() <= finalAtm2)
		.max(Comparator.comparingDouble(e -> e.getValue()[1]))
		.map(Map.Entry::getKey).orElse(atm - strikeGap * 5);

	// Support / Resistance (highest OI levels)
	int support    = putWall;
	int resistance = callWall;

	// Volume delta
	double volumeDelta = totalCallVol - totalPutVol;

	// Build strikes list
	int finalAtm3 = atm;
	List<StrikeData> strikes = chain.entrySet().stream()
		.filter(e -> Math.abs(e.getKey() - finalAtm3) <= strikeGap * 12)
		.map(e -> {
		    double[] d = e.getValue();
		    long cOI = (long) d[0], pOI = (long) d[1];
		    long cVol = (long) d[2], pVol = (long) d[3];
		    long totOI  = cOI + pOI, totVol = cVol + pVol;
		    return StrikeData.builder()
			    .strike(e.getKey())
			    .callOI(cOI).putOI(pOI)
			    .callVolume(cVol).putVolume(pVol)
			    .callIV(d[4]).putIV(d[5])
			    .callLTP(d[6]).putLTP(d[7])
			    .oiImbalance(totOI  > 0 ? (double)(cOI  - pOI)  / totOI  : 0)
			    .volumeImbalance(totVol > 0 ? (double)(cVol - pVol) / totVol : 0)
			    .ivSkew(d[4] - d[5])
			    .gamma(0.0)
			    .build();
		})
		.collect(Collectors.toList());

	// Regime classification
	String regime          = classifyRegime(pcr, volumeDelta, gammaFlip, spot, atm);
	String volatilityRegime = classifyVolatilityRegime(atmIv);
	String session         = classifySession();
	String marketStructure = classifyMarketStructure(spot, support, resistance, atm);

	return MarketStateDTO.builder()
		.symbol(symbol)
		.spot(spot)
		.futures(spot * 1.001)   // approximation until futures tick arrives
		.atm(atm)
		.pcr(Math.round(pcr * 100.0) / 100.0)
		.maxPain(computeMaxPain(chain))
		.gammaExposure(computeGex(chain))
		.gammaFlip(gammaFlip)
		.callWall(callWall)
		.putWall(putWall)
		.iv(Math.round(atmIv * 100.0) / 100.0)
		.ivRank(0.0)   // requires historical IV — set to 0 until implemented
		.oiChange(0.0) // requires previous snapshot — set to 0 until implemented
		.volumeDelta(volumeDelta)
		.marketStructure(marketStructure)
		.liquidity(atmIv > 20 ? "HIGH" : "MEDIUM")
		.regime(regime)
		.volatilityRegime(volatilityRegime)
		.session(session)
		.newsImpact("NONE")
		.support(support)
		.resistance(resistance)
		.strikes(strikes)
		.timestamp(System.currentTimeMillis())
		.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLASSIFICATION HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String classifyRegime(double pcr, double volDelta, double gammaFlip,
	    double spot, int atm) {
	if (pcr > 1.3 && volDelta > 0)   return "TRENDING_UP";
	if (pcr < 0.7 && volDelta < 0)   return "TRENDING_DOWN";
	if (spot > gammaFlip && gammaFlip > 0) return "BREAKOUT_ENVIRONMENT";
	if (spot < gammaFlip && gammaFlip > 0) return "BREAKDOWN_ENVIRONMENT";
	return "RANGE";
    }

    private String classifyVolatilityRegime(double iv) {
	if (iv > 20) return "HIGH_IV";
	if (iv > 14) return "EXPANDING";
	if (iv > 10) return "NORMAL";
	return "LOW_IV";
    }

    private String classifySession() {
	LocalTime now = LocalTime.now(IST);
	if (now.isBefore(LocalTime.of(9, 45)))   return "PRE_OPEN";
	if (now.isBefore(LocalTime.of(11, 0)))   return "OPENING";
	if (now.isBefore(LocalTime.of(14, 0)))   return "MIDDAY";
	return "CLOSING";
    }

    private String classifyMarketStructure(double spot, int support, int resistance, int atm) {
	if (spot > resistance) return "BREAKOUT";
	if (spot < support)    return "BREAKDOWN";
	if (spot > atm)        return "BULLISH";
	if (spot < atm)        return "BEARISH";
	return "SIDEWAYS";
    }

    private double computeGammaFlip(Map<Integer, double[]> chain, int atm) {
	// Gamma flip = strike where net call OI first crosses put OI going up from ATM
	for (Map.Entry<Integer, double[]> e : chain.entrySet()) {
	    int s = e.getKey();
	    if (s >= atm) {
		double[] d = e.getValue();
		if (d[0] > d[1]) return s;   // call OI > put OI → flip zone
	    }
	}
	return atm;
    }

    private double computeMaxPain(Map<Integer, double[]> chain) {
	double minPain = Double.MAX_VALUE;
	int    maxPainStrike = 0;
	for (Map.Entry<Integer, double[]> target : chain.entrySet()) {
	    int targetStrike = target.getKey();
	    double pain = 0;
	    for (Map.Entry<Integer, double[]> e : chain.entrySet()) {
		int s = e.getKey();
		double[] d = e.getValue();
		if (s > targetStrike) pain += (s - targetStrike) * d[0]; // call OI loss
		if (s < targetStrike) pain += (targetStrike - s) * d[1]; // put OI loss
	    }
	    if (pain < minPain) { minPain = pain; maxPainStrike = targetStrike; }
	}
	return maxPainStrike;
    }

    private double computeGex(Map<Integer, double[]> chain) {
	// Simplified GEX: sum of (callOI - putOI) * gamma proxy per strike
	double gex = 0;
	for (Map.Entry<Integer, double[]> e : chain.entrySet()) {
	    double[] d = e.getValue();
	    gex += (d[0] - d[1]) * 0.001;   // simplified gamma proxy
	}
	return Math.round(gex * 100.0) / 100.0;
    }

    private boolean isMarketHours() {
	LocalTime now = LocalTime.now(IST);
	return !now.isBefore(LocalTime.parse(marketOpenStr))
		&& !now.isAfter(LocalTime.parse(marketCloseStr));
    }

    private double toDouble(Object v) {
	try { return v == null ? 0.0 : Double.parseDouble(v.toString()); }
	catch (Exception e) { return 0.0; }
    }

    private int toInt(Object v) {
	try { return v == null ? 0 : (int) Double.parseDouble(v.toString()); }
	catch (Exception e) { return 0; }
    }
}
