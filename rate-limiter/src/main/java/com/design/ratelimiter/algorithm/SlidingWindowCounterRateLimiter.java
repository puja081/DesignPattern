package com.design.ratelimiter.algorithm;

import com.design.ratelimiter.core.RateLimiter;
import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.model.RateLimitResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SLIDING WINDOW COUNTER ALGORITHM (Hybrid)
 *
 * How it works:
 *   - Combines Fixed Window's low memory with Sliding Window Log's precision.
 *   - Maintains counters for the CURRENT and PREVIOUS window.
 *   - Estimates the request count using a weighted average:
 *
 *     estimatedCount = previousCount * overlapRatio + currentCount
 *
 *     where overlapRatio = (windowSize - elapsedInCurrentWindow) / windowSize
 *
 * Example (5 req/min limit):
 *   Previous window (0:00-1:00): 4 requests
 *   Current  window (1:00-2:00): 2 requests so far
 *   Current time: 1:20 (20s into current window)
 *
 *   overlapRatio = (60 - 20) / 60 = 0.667
 *   estimated    = 4 * 0.667 + 2  = 4.67 → rounds to 4
 *   remaining    = 5 - 4 = 1 → ALLOWED
 *
 * Characteristics:
 *   - Good balance of precision and memory.
 *   - Used by Cloudflare, Redis-based limiters.
 *   - Smooths out the boundary burst problem.
 *
 * Time: O(1)    Space: O(n) — only 2 counters per client
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final RateLimiterConfig config;
    private final ConcurrentMap<String, SlidingCounter> counters = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        SlidingCounter counter = counters.computeIfAbsent(clientId, k -> new SlidingCounter(config));
        return counter.tryIncrement();
    }

    @Override
    public String algorithmName() {
        return "Sliding Window Counter";
    }

    private static class SlidingCounter {
        private final int maxRequests;
        private final long windowMillis;
        private long currentWindowStart;
        private int currentCount;
        private int previousCount;

        SlidingCounter(RateLimiterConfig config) {
            this.maxRequests = config.maxRequests();
            this.windowMillis = config.windowMillis();
            this.currentWindowStart = System.currentTimeMillis();
            this.currentCount = 0;
            this.previousCount = 0;
        }

        synchronized RateLimitResult tryIncrement() {
            long now = System.currentTimeMillis();
            advanceWindow(now);

            long elapsedInCurrentWindow = now - currentWindowStart;
            double overlapRatio = (windowMillis - elapsedInCurrentWindow) / (double) windowMillis;
            int estimatedCount = (int) (previousCount * overlapRatio) + currentCount;

            if (estimatedCount < maxRequests) {
                currentCount++;
                int newEstimate = (int) (previousCount * overlapRatio) + currentCount;
                return RateLimitResult.allowed(maxRequests, maxRequests - newEstimate);
            }

            long retryAfter = windowMillis - elapsedInCurrentWindow;
            return RateLimitResult.rejected(maxRequests, Math.max(retryAfter, 1));
        }

        private void advanceWindow(long now) {
            long elapsed = now - currentWindowStart;
            if (elapsed >= windowMillis) {
                long windowsElapsed = elapsed / windowMillis;
                if (windowsElapsed == 1) {
                    previousCount = currentCount;
                } else {
                    previousCount = 0;
                }
                currentCount = 0;
                currentWindowStart += windowsElapsed * windowMillis;
            }
        }
    }
}
