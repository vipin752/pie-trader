package com.pietrader.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TradeStateService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public TradeStateService(StringRedisTemplate redis) {
	this.redis = redis;
    }

    private String key(String symbol) {
	return "TRADE_STATE:" + symbol;
    }

    // ================= GET =================
    public TradeState get(String symbol) {
	try {
	    String json = redis.opsForValue().get(key(symbol));

	    if (json == null) return null;

	    return mapper.readValue(json, TradeState.class);

	} catch (Exception e) {
	    log.error("Error reading state", e);
	    return null;
	}
    }

    // ================= SAVE =================
    public void save(TradeState state) {
	try {
	    String json = mapper.writeValueAsString(state);

	    redis.opsForValue().set(
		    key(state.getSymbol()),
		    json,
		    1, TimeUnit.DAYS
	    );

	    log.info("State saved: {}", state);

	} catch (Exception e) {
	    log.error("Error saving state", e);
	}
    }

    // ================= CLEAR =================
    public void clear(String symbol) {
	redis.delete(key(symbol));
	log.info("State cleared for {}", symbol);
    }
}
