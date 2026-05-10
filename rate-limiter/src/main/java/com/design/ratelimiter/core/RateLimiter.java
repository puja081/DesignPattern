package com.design.ratelimiter.core;

import com.design.ratelimiter.model.RateLimitResult;

/**
 * Strategy interface for rate limiting.
 * Each implementation encapsulates a different algorithm
 * (Token Bucket, Fixed Window, Sliding Window Log, Sliding Window Counter).
 */
public interface RateLimiter {

    RateLimitResult tryAcquire(String clientId);

    String algorithmName();
}
