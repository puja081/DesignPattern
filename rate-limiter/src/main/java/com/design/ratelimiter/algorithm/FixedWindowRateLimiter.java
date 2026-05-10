package com.design.ratelimiter.algorithm;

import com.design.ratelimiter.core.RateLimiter;
import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.model.RateLimitResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * FIXED WINDOW COUNTER ALGORITHM
 *
 * How it works:
 *   - Time is divided into fixed windows (e.g., every 60 seconds).
 *   - Each window has a counter starting at 0.
 *   - Each request increments the counter.
 *   - If counter > maxRequests, reject.
 *   - Counter resets when the window rolls over.
 *
 * Characteristics:
 *   - Simplest to implement.
 *   - BOUNDARY BURST PROBLEM: a client can make 2x requests at the
 *     boundary of two windows (N requests at end of window 1 +
 *     N requests at start of window 2).
 *   - Used when simplicity is preferred over precision.
 *
 * Time: O(1)    Space: O(n)
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private final RateLimiterConfig config;
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        WindowCounter counter = counters.computeIfAbsent(clientId, k -> new WindowCounter(config));
        return counter.tryIncrement();
    }

    @Override
    public String algorithmName() {
        return "Fixed Window Counter";
    }

    private static class WindowCounter {
        private final int maxRequests;
        private final long windowMillis;
        private long windowStart;
        private int count;

        WindowCounter(RateLimiterConfig config) {
            this.maxRequests = config.maxRequests();
            this.windowMillis = config.windowMillis();
            this.windowStart = System.currentTimeMillis();
            this.count = 0;
        }

        synchronized RateLimitResult tryIncrement() {
            long now = System.currentTimeMillis();

            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }

            if (count < maxRequests) {
                count++;
                return RateLimitResult.allowed(maxRequests, maxRequests - count);
            }

            long retryAfter = windowMillis - (now - windowStart);
            return RateLimitResult.rejected(maxRequests, retryAfter);
        }
    }
}
