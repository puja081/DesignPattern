package com.design.ratelimiter.core;

import java.time.Duration;

/**
 * Configuration for a rate limiter.
 * maxRequests: maximum requests allowed in the time window.
 * window: the duration of the time window.
 */
public record RateLimiterConfig(int maxRequests, Duration window) {

    public static RateLimiterConfig of(int maxRequests, Duration window) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("window must be positive");
        return new RateLimiterConfig(maxRequests, window);
    }

    public long windowMillis() {
        return window.toMillis();
    }
}
