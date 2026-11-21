package vn.vshop.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Fixed-window attempt counter kept in memory. It protects public endpoints that cost something
 * (account creation, e-mail sending) for callers that do not pass through the web BFF, such as the
 * mobile app. It is per API instance; the BFF keeps a shared limit in Redis for the web.
 */
@Component
public class AttemptLimiter {
    private record Window(long startedAtMillis, int count) {}

    private static final int MAX_KEYS = 50_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** True while fewer than {@code limit} attempts were made for the key in the window. */
    public boolean tryAcquire(String key, int limit, Duration window) {
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
