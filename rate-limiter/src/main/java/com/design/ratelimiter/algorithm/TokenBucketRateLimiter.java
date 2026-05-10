package com.design.ratelimiter.algorithm;

import com.design.ratelimiter.core.RateLimiter;
import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.model.RateLimitResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * TOKEN BUCKET ALGORITHM
 *
 * How it works:
 *   - Each client has a bucket that holds up to 'maxTokens' tokens.
 *   - Tokens are added at a steady rate (maxRequests / windowMillis).
 *   - Each request consumes one token.
 *   - If the bucket is empty, the request is rejected.
 *
 * Characteristics:
 *   - Allows short bursts (up to maxTokens at once).
 *   - Smooth refill over time.
 *   - Most widely used: AWS API Gateway, Stripe, Google Cloud.
 *
 * Time: O(1) per request    Space: O(n) where n = number of clients
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimiterConfig config;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(RateLimiterConfig config) {
        this.config = config;
    }

    @Override
    public RateLimitResult tryAcquire(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, k -> new TokenBucket(config));
        return bucket.tryConsume();
    }

    @Override
    public String algorithmName() {
        return "Token Bucket";
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final double refillRatePerMs;
        private double currentTokens;
        private long lastRefillTime;

        TokenBucket(RateLimiterConfig config) {
            this.maxTokens = config.maxRequests();
            this.refillRatePerMs = (double) config.maxRequests() / config.windowMillis();
            this.currentTokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized RateLimitResult tryConsume() {
            refill();

            if (currentTokens >= 1.0) {
                currentTokens -= 1.0;
                return RateLimitResult.allowed(maxTokens, (int) currentTokens);
            }

            long waitMs = (long) ((1.0 - currentTokens) / refillRatePerMs);
            return RateLimitResult.rejected(maxTokens, waitMs);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            double tokensToAdd = elapsed * refillRatePerMs;
            currentTokens = Math.min(maxTokens, currentTokens + tokensToAdd);
            lastRefillTime = now;
        }
    }
}
