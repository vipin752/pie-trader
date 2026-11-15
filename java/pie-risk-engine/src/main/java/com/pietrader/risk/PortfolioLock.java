package com.pietrader.risk;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cross-instance lock so two replicas (two pods, a rolling-deployment
 * overlap, …) never both act on the same correlated signal at once
 * (Critical Safety #5 "distributed locking", #7 "protection against two
 * replicas simultaneously executing the same signal").
 *
 * <p>Uses Redis {@code SET key value NX PX} — atomic acquire with a TTL, so
 * a crashed holder self-releases instead of deadlocking the group forever.
 * This is a lock, not the source of truth: Postgres (day ledger, decision
 * log) remains that. If Redis is briefly unavailable, {@link #tryAcquire}
 * fails closed (returns false) rather than letting two replicas race —
 * see spec Critical Safety #10 "safe operation when ... connectivity is
 * unavailable".
 */
@Component
public class PortfolioLock {

    private final StringRedisTemplate redis;

    public PortfolioLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return a release token if acquired, or null if another replica holds it (or Redis is down). */
    public String tryAcquire(String correlationGroup, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent("pie:lock:" + correlationGroup, token, ttl);
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception redisUnavailable) {
            return null;  // fail closed — spec Critical Safety #10
        }
    }

    /** Releases only if this caller still holds it — never release someone else's lock. */
    public void release(String correlationGroup, String token) {
        String key = "pie:lock:" + correlationGroup;
        try {
            String held = redis.opsForValue().get(key);
            if (token.equals(held)) {
                redis.delete(key);
            }
        } catch (Exception redisUnavailable) {
            // The TTL still expires it; nothing more we can safely do here.
        }
    }
}
