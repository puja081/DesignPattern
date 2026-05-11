package com.design.ratelimiter.controller;

import com.design.ratelimiter.core.RateLimiterManager;
import com.design.ratelimiter.model.RateLimitResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot REST Controller for rate-limited API endpoints.
 *
 * This is the CONTROLLER equivalent of the plain Java RateLimiterHandler.
 * Compare side by side to see how Spring Boot simplifies the code:
 *
 *   Plain Java (RateLimiterHandler):
 *     - Manually parse URL path and query params
 *     - Manually write JSON response bytes
 *     - Manually set status code and headers
 *     - Manually manage HttpExchange input/output streams
 *
 *   Spring Boot (this class):
 *     - @PathVariable extracts {resource} from URL
 *     - @RequestParam extracts clientId from query string
 *     - Return Map → Spring auto-converts to JSON
 *     - ResponseEntity sets status code + headers
 *
 * Endpoints:
 *   GET /api/ratelimit/{resource}?clientId={id}   → rate-limited request
 *   GET /api/ratelimit/{resource}/status?clientId={id} → check remaining quota
 */
@RestController
@RequestMapping("/api/ratelimit")
public class RateLimiterController {

    /**
     * GET /api/ratelimit/{resource}?clientId={id}
     *
     * Makes a rate-limited API call.
     * Returns 200 if allowed, 429 if rate limit exceeded.
     */
    @GetMapping("/{resource}")
    public ResponseEntity<Map<String, Object>> handleRequest(
            @PathVariable String resource,
            @RequestParam(defaultValue = "anonymous") String clientId) {

        RateLimiterManager manager = RateLimiterManager.getInstance();
        RateLimitResult result;

        try {
            result = manager.tryAcquire(resource, clientId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No rate limiter registered for: " + resource));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("resource", resource);
        body.put("clientId", clientId);

        if (result.allowed()) {
            body.put("message", "Request allowed");
            body.put("remaining", result.remaining());

            return ResponseEntity.ok()
                    .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                    .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                    .body(body);
        } else {
            long retryAfterSec = Math.max(1, result.retryAfterMillis() / 1000);
            body.put("error", "Rate limit exceeded");
            body.put("retryAfterMs", result.retryAfterMillis());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                    .header("X-RateLimit-Remaining", "0")
                    .header("Retry-After", String.valueOf(retryAfterSec))
                    .body(body);
        }
    }
}
