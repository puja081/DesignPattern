package com.design.ratelimiter;

import com.design.ratelimiter.algorithm.*;
import com.design.ratelimiter.api.RateLimiterServer;
import com.design.ratelimiter.core.RateLimiter;
import com.design.ratelimiter.core.RateLimiterConfig;
import com.design.ratelimiter.core.RateLimiterFactory;
import com.design.ratelimiter.core.RateLimiterManager;
import com.design.ratelimiter.model.RateLimitResult;

import java.time.Duration;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("=".repeat(70));
        System.out.println("  PART 1 — Algorithm Comparison (5 req per 2 seconds)");
        System.out.println("=".repeat(70));
        demoAllAlgorithms();

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  PART 2 — REST API with Rate Limiting");
        System.out.println("=".repeat(70));
        demoRestServer();
    }

    private static void demoAllAlgorithms() throws InterruptedException {
        RateLimiterConfig config = RateLimiterConfig.of(5, Duration.ofSeconds(2));

        RateLimiter[] limiters = {
                new TokenBucketRateLimiter(config),
                new FixedWindowRateLimiter(config),
                new SlidingWindowLogRateLimiter(config),
                new SlidingWindowCounterRateLimiter(config)
        };

        for (RateLimiter limiter : limiters) {
            System.out.println("\n--- " + limiter.algorithmName() + " ---");
            String clientId = "user-1";

            for (int i = 1; i <= 7; i++) {
                RateLimitResult result = limiter.tryAcquire(clientId);
                System.out.printf("  Request %d: %s (remaining=%d)%n",
                        i,
                        result.allowed() ? "ALLOWED" : "REJECTED (retry in " + result.retryAfterMillis() + "ms)",
                        result.remaining());
            }

            System.out.println("  ... waiting 2.1 seconds for window to reset ...");
            Thread.sleep(2100);

            RateLimitResult afterWait = limiter.tryAcquire(clientId);
            System.out.printf("  Request 8 (after wait): %s (remaining=%d)%n",
                    afterWait.allowed() ? "ALLOWED" : "REJECTED",
                    afterWait.remaining());
        }
    }

    private static void demoRestServer() throws Exception {
        RateLimiterManager manager = RateLimiterManager.getInstance();
        manager.register("search", RateLimiterFactory.Algorithm.TOKEN_BUCKET,
                RateLimiterConfig.of(10, Duration.ofMinutes(1)));
        manager.register("login", RateLimiterFactory.Algorithm.SLIDING_WINDOW_LOG,
                RateLimiterConfig.of(3, Duration.ofMinutes(1)));

        RateLimiterServer server = new RateLimiterServer(8090);
        server.start();

        System.out.println();
        System.out.println("Registered rate limits:");
        System.out.println("  /api/search  → Token Bucket,       10 req/min");
        System.out.println("  /api/login   → Sliding Window Log,  3 req/min");
        System.out.println();
        System.out.println("Try these commands:");
        System.out.println("  curl 'http://localhost:8090/api/search?clientId=user1'");
        System.out.println("  curl 'http://localhost:8090/api/login?clientId=user1'");
        System.out.println("  # Hit login 4 times quickly to see 429 Too Many Requests");
        System.out.println();
        System.out.println("Press Ctrl+C to stop.");
    }
}
