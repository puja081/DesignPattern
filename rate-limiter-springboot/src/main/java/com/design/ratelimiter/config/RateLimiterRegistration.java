package com.design.ratelimiter.config;

import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.core.RateLimiterFactory;
import com.design.ratelimiter.core.RateLimiterManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Registers rate limiting rules at application startup.
 * In production, these rules would come from a database or config service.
 */
@Component
public class RateLimiterRegistration {

    @PostConstruct
    public void registerRules() {
        RateLimiterManager manager = RateLimiterManager.getInstance();

        manager.register("search",
                RateLimiterFactory.Algorithm.TOKEN_BUCKET,
                RateLimiterConfig.of(10, Duration.ofMinutes(1)));

        manager.register("login",
                RateLimiterFactory.Algorithm.SLIDING_WINDOW_LOG,
                RateLimiterConfig.of(3, Duration.ofMinutes(1)));

        manager.register("upload",
                RateLimiterFactory.Algorithm.SLIDING_WINDOW_COUNTER,
                RateLimiterConfig.of(5, Duration.ofMinutes(1)));

        System.out.println("Rate limiting rules registered:");
        System.out.println("  /api/ratelimit/search  → Token Bucket,           10 req/min");
        System.out.println("  /api/ratelimit/login   → Sliding Window Log,      3 req/min");
        System.out.println("  /api/ratelimit/upload  → Sliding Window Counter,  5 req/min");
    }
}
