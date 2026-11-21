package vn.vshop.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window attempt counter for public endpoints that cost something (account creation, e-mail
 * sending), including callers that skip the web BFF such as the mobile app.
 *
 * <p>With {@code vshop.ratelimit.redis=true} the counters live in Redis, so every API instance
 * shares them. If Redis then fails the attempt is refused (an abuse guard must not switch itself
 * off). Without it (tests, a single dev process) counters are kept in memory per instance.
 */
@Component
public class AttemptLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(AttemptLimiter.class);
    private static final String PREFIX = "vshop:api-rate:";
    private static final int MAX_KEYS = 50_000;

    private record Window(long startedAtMillis, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final StringRedisTemplate redis;

    AttemptLimiter(
            Clock clock,
            ObjectProvider<StringRedisTemplate> redis,
            @Value("${vshop.ratelimit.redis:false}") boolean useRedis) {
        this.clock = clock;
        this.redis = useRedis ? redis.getIfAvailable() : null;
    }

    /** True while fewer than {@code limit} attempts were made for the key in the window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (redis != null) {
            return viaRedis(key, limit, window);
        }
        return inMemory(key, limit, window);
    }

    private boolean viaRedis(String key, int limit, Duration window) {
        try {
            String redisKey = PREFIX + key;
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return false;
            }
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= limit;
        } catch (RuntimeException e) {
            LOG.warn("Rate limit store unavailable, refusing the attempt: {}", e.toString());
            return false;
        }
    }

    private boolean inMemory(String key, int limit, Duration window) {
        long now = clock.millis();
        if (windows.size() > MAX_KEYS) {
            windows.entrySet()
                    .removeIf(e -> now - e.getValue().startedAtMillis() > window.toMillis());
        }
        boolean[] allowed = {false};
        windows.compute(
                key,
                (k, current) -> {
                    if (current == null || now - current.startedAtMillis() >= window.toMillis()) {
                        allowed[0] = limit > 0;
                        return new Window(now, 1);
                    }
                    if (current.count() < limit) {
                        allowed[0] = true;
                        return new Window(current.startedAtMillis(), current.count() + 1);
                    }
                    return current;
                });
        return allowed[0];
    }
}
