package com.design.ratelimiter.algorithm;

import com.design.ratelimiter.core.RateLimiter;
import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.model.RateLimitResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SLIDING WINDOW LOG ALGORITHM
 *
 * How it works:
 *   - Keeps a log (queue) of ALL request timestamps per client.
 *   - On each request, evict timestamps older than (now - window).
 *   - If remaining log size < maxRequests, allow and add timestamp.
 *   - Otherwise, reject.
 *
 * Characteristics:
 *   - Most precise — no boundary burst problem.
 *   - Higher memory usage: stores every request timestamp.
 *   - Good for low-volume, high-precision use cases (e.g., login attempts).
 *
 * Time: O(n) worst case for cleanup, amortized O(1)
 * Space: O(maxRequests) per client
 */
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final RateLimiterConfig config;
    private final ConcurrentMap<String, RequestLog> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        RequestLog log = logs.computeIfAbsent(clientId, k -> new RequestLog(config));
        return log.tryRecord();
    }

    @Override
    public String algorithmName() {
        return "Sliding Window Log";
    }

    private static class RequestLog {
        private final int maxRequests;
        private final long windowMillis;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        RequestLog(RateLimiterConfig config) {
            this.maxRequests = config.maxRequests();
            this.windowMillis = config.windowMillis();
        }

        synchronized RateLimitResult tryRecord() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMillis;

            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                return RateLimitResult.allowed(maxRequests, maxRequests - timestamps.size());
            }

            long oldestInWindow = timestamps.peekFirst();
            long retryAfter = oldestInWindow + windowMillis - now;
            return RateLimitResult.rejected(maxRequests, Math.max(retryAfter, 1));
        }
    }
}
