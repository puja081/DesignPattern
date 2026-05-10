package com.design.ratelimiter.model;

/**
 * Result of a rate limit check.
 * Contains whether the request is allowed and metadata for HTTP headers.
 */
public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long retryAfterMillis
) {
    public static RateLimitResult allowed(int limit, int remaining) {
        return new RateLimitResult(true, limit, remaining, 0);
    }

    public static RateLimitResult rejected(int limit, long retryAfterMillis) {
        return new RateLimitResult(false, limit, 0, retryAfterMillis);
    }
}
