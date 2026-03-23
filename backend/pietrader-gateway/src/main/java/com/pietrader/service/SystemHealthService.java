package com.pietrader.service;

import com.pietrader.broker.angel.AngelSessionManager;
import com.pietrader.broker.angel.AngelWebSocketClient;
import com.pietrader.client.PythonAnalyticsClient;
import com.pietrader.execution.PositionManager;
import com.pietrader.state.TradeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PIE TRADER — SystemHealthService
 *
 * Checks all 7 system components from the production checklist: 1. broker_ws    — Angel WebSocket connected + last tick
 * age 2. kafka        — Admin client topic list reachable 3. python_engine— HTTP GET /health on Python FastAPI 4.
 * java_execution — this JVM is running (trivially UP) 5. redis        — PING command 6. postgres     — SELECT 1 7.
 * ui_ws        — derived from kafka (trade.events flowing)
 *
 * All checks are non-throwing — each catch returns DOWN with reason. The overall status is UP only when ALL critical
 * components are UP. Critical: broker_ws, kafka, python_engine, redis, postgres. Non-critical: ui_ws (derived),
 * java_execution (self).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthService
{

    private final AngelWebSocketClient webSocketClient;
    private final AngelSessionManager sessionManager;
    private final PythonAnalyticsClient pythonClient;
    private final StringRedisTemplate redis;
    private final KafkaAdmin kafkaAdmin;
    private final DataSource dataSource;
    private final TradeStateManager stateManager;
    private final PositionManager positionManager;

    @Value("${PYTHON_ANALYTICS_URL:http://localhost:8000}")
    private String pythonUrl;

    private static final DateTimeFormatter IST_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
	    .withZone(ZoneId.of("Asia/Kolkata"));

    private static final List<String> SYMBOLS = List.of("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY");

    private static final List<String> KAFKA_TOPICS = List.of("pie.market.ticks", "pie.analytics.results",
	    "pie.trade.events", "pie.position.events", "pie.squareoff.signals", "pie.alerts");

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN HEALTH CHECK
    // ─────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getHealth()
    {
	Map<String, Object> health = new LinkedHashMap<>();
	health.put("timestamp", System.currentTimeMillis());
	health.put("time_ist", IST_FMT.format(Instant.now()));

	// ── Component checks ──────────────────────────────────────────────
	Map<String, Object> brokerWs = checkBrokerWs();
	Map<String, Object> kafka = checkKafka();
	Map<String, Object> python = checkPython();
	Map<String, Object> redisCheck = checkRedis();
	Map<String, Object> postgres = checkPostgres();
	Map<String, Object> javaExec = checkJavaExecution();
	Map<String, Object> uiWs = checkUiWs(kafka);

	health.put("broker_ws", brokerWs);
	health.put("kafka", kafka);
	health.put("python_engine", python);
	health.put("java_execution", javaExec);
	health.put("redis", redisCheck);
	health.put("postgres", postgres);
	health.put("ui_ws", uiWs);

	// ── Timing probes ─────────────────────────────────────────────────
	health.put("last_tick_time", getLastTickTime());
	health.put("last_decision_time", getLastDecisionTime());
	health.put("last_trade_time", getLastTradeTime());
	health.put("last_tick_age_sec", getTickAgeSeconds());

	// ── Active positions ──────────────────────────────────────────────
	health.put("active_positions", positionManager.getActiveSymbols().size());
	health.put("trading_mode", stateManager.getMode());

	// ── ExecutionGate state per symbol ────────────────────────────────
	health.put("execution_gate", buildGateState());

	// ── Overall status ────────────────────────────────────────────────
	boolean allCriticalUp = "UP".equals(brokerWs.get("status")) && "UP".equals(kafka.get("status")) && "UP".equals(
		python.get("status")) && "UP".equals(redisCheck.get("status")) && "UP".equals(postgres.get("status"));

	health.put("status", allCriticalUp ? "UP" : "DEGRADED");
	health.put("healthy", allCriticalUp);

	return health;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. BROKER WebSocket
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkBrokerWs()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	try
	{
	    boolean connected = webSocketClient.isConnected();
	    boolean hasSession = sessionManager.getFeedToken() != null && !sessionManager.getFeedToken().isBlank();

	    m.put("status", connected ? "UP" : "DOWN");
	    m.put("ws_connected", connected);
	    m.put("session_active", hasSession);
	    m.put("last_tick_age_sec", getTickAgeSeconds());

	    if(!connected && !hasSession)
	    {
		m.put("message", "Not logged in. POST /api/broker/login with TOTP.");
	    }
	    else if(!connected)
	    {
		m.put("message", "Session OK but WebSocket disconnected. Reconnecting...");
	    }
	    else
	    {
		m.put("message", "Angel SmartStream connected");
	    }
	}
	catch(Exception e)
	{
	    m.put("status", "DOWN");
	    m.put("message", "Broker check error: " + e.getMessage());
	}
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. KAFKA
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkKafka()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties()))
	{
	    Set<String> existingTopics = admin.listTopics().names().get();
	    List<String> found = new ArrayList<>();
	    List<String> missing = new ArrayList<>();
	    for(String t : KAFKA_TOPICS)
	    {
		(existingTopics.contains(t) ? found : missing).add(t);
	    }
	    boolean allOk = missing.isEmpty();
	    m.put("status", allOk ? "UP" : "DEGRADED");
	    m.put("topics_found", found);
	    m.put("topics_missing", missing);
	    m.put("broker", "localhost:9092");
	}
	catch(Exception e)
	{
	    m.put("status", "DOWN");
	    m.put("message", "Kafka unreachable: " + e.getMessage());
	}
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PYTHON ENGINE
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkPython()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	try
	{
	    boolean up = pythonClient.isHealthy();
	    m.put("status", up ? "UP" : "DOWN");
	    m.put("endpoint", pythonUrl);
	    m.put("message", up ?
		    "Python analytics engine reachable" :
		    "Python engine not responding on " + pythonUrl + "/health");

	    // Check if we're getting decisions (signal cache age)
	    String lastDecision = redis.opsForValue().get("system:last_decision_time");
	    if(lastDecision != null)
	    {
		long age = (System.currentTimeMillis() - Long.parseLong(lastDecision)) / 1000;
		m.put("last_decision_age_sec", age);
		m.put("decisions_flowing", age < 30); // should be every 2s
	    }
	    else
	    {
		m.put("decisions_flowing", false);
		m.put("last_decision_age_sec", -1);
	    }
	}
	catch(Exception e)
	{
	    m.put("status", "DOWN");
	    m.put("message", "Python check error: " + e.getMessage());
	}
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. JAVA EXECUTION (self)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkJavaExecution()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	m.put("status", "UP");
	m.put("trading_mode", stateManager.getMode());
	m.put("active_positions", positionManager.getActiveSymbols().size());
	m.put("message", "Java execution engine running");
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REDIS
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkRedis()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	try
	{
	    String pong = redis.getConnectionFactory() != null ?
		    redis.getConnectionFactory().getConnection().ping() :
		    null;
	    boolean ok = "PONG".equalsIgnoreCase(pong);
	    m.put("status", ok ? "UP" : "DOWN");
	    m.put("ping", pong);

	    // Show key state summary
	    Map<String, Object> keys = new LinkedHashMap<>();
	    keys.put("trade_mode", stateManager.getMode());
	    for(String sym : List.of("NIFTY", "BANKNIFTY"))
	    {
		keys.put("lock_" + sym, stateManager.isLocked(sym));
		keys.put("position_" + sym, stateManager.hasActivePosition(sym));
		keys.put("cooldown_" + sym, stateManager.isInCooldown(sym));
		keys.put("trades_today_" + sym, stateManager.getDailyTradeCount(sym));
		keys.put("pnl_" + sym, stateManager.getDailyPnl(sym));
	    }
	    m.put("state", keys);
	}
	catch(Exception e)
	{
	    m.put("status", "DOWN");
	    m.put("message", "Redis unreachable: " + e.getMessage());
	}
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. POSTGRES
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkPostgres()
    {
	Map<String, Object> m = new LinkedHashMap<>();
	try
	{
	    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
	    Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
	    boolean ok = result != null && result == 1;
	    m.put("status", ok ? "UP" : "DOWN");
	    m.put("message", ok ? "PostgreSQL connected" : "SELECT 1 returned unexpected result");
	}
	catch(Exception e)
	{
	    m.put("status", "DOWN");
	    m.put("message", "Postgres unreachable: " + e.getMessage());
	}
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. UI WebSocket (derived — kafka trade.events flowing = UI can receive)
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> checkUiWs(Map<String, Object> kafkaStatus)
    {
	Map<String, Object> m = new LinkedHashMap<>();
	boolean kafkaOk = "UP".equals(kafkaStatus.get("status")) || "DEGRADED".equals(kafkaStatus.get("status"));
	m.put("status", kafkaOk ? "UP" : "DOWN");
	m.put("message", kafkaOk ?
		"Kafka OK — UI can receive trade.events and position.events" :
		"Kafka DOWN — UI WebSocket feed unavailable");
	return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXECUTION GATE STATE per symbol
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> buildGateState()
    {
	Map<String, Object> gates = new LinkedHashMap<>();
	for(String sym : SYMBOLS)
	{
	    Map<String, Object> g = new LinkedHashMap<>();
	    boolean hasPos = stateManager.hasActivePosition(sym);
	    boolean locked = stateManager.isLocked(sym);
	    boolean cooldown = stateManager.isInCooldown(sym);
	    boolean maxLoss = stateManager.isMaxLossBreached(sym);
	    long trades = stateManager.getDailyTradeCount(sym);
	    boolean signalOk = stateManager.getCachedSignal(sym) != null;

	    g.put("has_active_position", hasPos);
	    g.put("is_locked", locked);
	    g.put("in_cooldown", cooldown);
	    g.put("max_loss_breached", maxLoss);
	    g.put("daily_trades", trades);
	    g.put("has_signal", signalOk);
	    g.put("daily_pnl", stateManager.getDailyPnl(sym));

	    // Compute gate state
	    String gateState;
	    if(maxLoss)
		gateState = "BLOCKED_MAX_LOSS";
	    else if(hasPos)
		gateState = "BLOCKED_ACTIVE_POSITION";
	    else if(cooldown)
		gateState = "BLOCKED_COOLDOWN";
	    else if(locked)
		gateState = "BLOCKED_LOCKED";
	    else if(!signalOk)
		gateState = "WAITING_FOR_SIGNAL";
	    else
		gateState = "READY";

	    g.put("gate_state", gateState);
	    gates.put(sym, g);
	}
	return gates;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIMING HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String getLastTickTime()
    {
	try
	{
	    String v = redis.opsForValue().get("system:last_tick_time");
	    if(v == null)
		return "—";
	    return IST_FMT.format(Instant.ofEpochMilli(Long.parseLong(v)));
	}
	catch(Exception e)
	{
	    return "—";
	}
    }

    private String getLastDecisionTime()
    {
	try
	{
	    String v = redis.opsForValue().get("system:last_decision_time");
	    if(v == null)
		return "—";
	    return IST_FMT.format(Instant.ofEpochMilli(Long.parseLong(v)));
	}
	catch(Exception e)
	{
	    return "—";
	}
    }

    private String getLastTradeTime()
    {
	try
	{
	    // Check across all symbols
	    for(String sym : SYMBOLS)
	    {
		Long t = stateManager.getLastTradeTime(sym);
		if(t != null && t > 0)
		    return IST_FMT.format(Instant.ofEpochMilli(t));
	    }
	    return "—";
	}
	catch(Exception e)
	{
	    return "—";
	}
    }

    private long getTickAgeSeconds()
    {
	try
	{
	    String v = redis.opsForValue().get("system:last_tick_time");
	    if(v == null)
		return -1L;
	    return (System.currentTimeMillis() - Long.parseLong(v)) / 1000;
	}
	catch(Exception e)
	{
	    return -1L;
	}
    }
}
