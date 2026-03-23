package com.pietrader;

import com.pietrader.broker.angel.AngelWebSocketClient;
import com.pietrader.broker.angel.AngelSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Spring context integration test.
 *
 * @ActiveProfiles("test") loads application-test.properties:
 *   - H2 in-memory DB
 *   - angel.startup.enabled=false (no TOTP prompt)
 *
 * @MockBean replaces beans that require external connections at startup:
 *   - StringRedisTemplate  → no Redis server needed
 *   - AngelWebSocketClient → no WS connect
 *   - AngelSessionManager  → no Angel login
 */
@SpringBootTest
@ActiveProfiles("test")
class PietraderGatewayApplicationTests {

    // Mock infrastructure beans that need external connections
    @MockitoBean
    StringRedisTemplate    stringRedisTemplate;
    @MockitoBean AngelWebSocketClient   angelWebSocketClient;
    @MockitoBean AngelSessionManager    angelSessionManager;

    @Test
    void contextLoads() {
        // Passes if Spring context starts without errors
    }
}
