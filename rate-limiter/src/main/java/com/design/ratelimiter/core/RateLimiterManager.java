package com.design.ratelimiter.core;

import com.design.ratelimiter.model.RateLimitResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages multiple named rate limiters — one per API endpoint or resource.
 *
 * Example:
 *   manager.register("login",  SLIDING_WINDOW_LOG, 5 req/min)   // strict
 *   manager.register("search", TOKEN_BUCKET,       100 req/min)  // lenient
 *
 *   manager.tryAcquire("login", "user-123")
 */
public final class RateLimiterManager {

    private static final RateLimiterManager INSTANCE = new RateLimiterManager();

    private final ConcurrentMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    private RateLimiterManager() {}

    public static RateLimiterManager getInstance() {
        return INSTANCE;
    }

    public void register(String resource, RateLimiterFactory.Algorithm algorithm, RateLimiterConfig config) {
        limiters.put(resource, RateLimiterFactory.create(algorithm, config));
    }

    public RateLimitResult tryAcquire(String resource, String clientId) {
        RateLimiter limiter = limiters.get(resource);
        if (limiter == null) {
            throw new IllegalArgumentException("No rate limiter registered for: " + resource);
        }
        return limiter.tryAcquire(clientId);
    }

    public RateLimiter getLimiter(String resource) {
        return limiters.get(resource);
    }
}
