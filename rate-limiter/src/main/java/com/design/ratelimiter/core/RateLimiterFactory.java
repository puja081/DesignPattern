package com.design.ratelimiter.core;

import com.design.ratelimiter.algorithm.*;

/**
 * Factory to create RateLimiter instances for a given algorithm.
 */
public final class RateLimiterFactory {

    public enum Algorithm {
        TOKEN_BUCKET,
        FIXED_WINDOW,
        SLIDING_WINDOW_LOG,
        SLIDING_WINDOW_COUNTER
    }

    private RateLimiterFactory() {}

    public static RateLimiter create(Algorithm algorithm, RateLimiterConfig config) {
        return switch (algorithm) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(config);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(config);
        };
    }
}
