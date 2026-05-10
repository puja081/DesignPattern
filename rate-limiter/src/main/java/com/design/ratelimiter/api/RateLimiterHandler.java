package com.design.ratelimiter.api;

import com.design.ratelimiter.core.RateLimiterManager;
import com.design.ratelimiter.model.RateLimitResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * REST handler that demonstrates rate limiting on an API endpoint.
 *
 * Endpoints:
 *   GET /api/{resource}?clientId={id}  — rate-limited API call
 *
 * Returns standard rate limit headers:
 *   X-RateLimit-Limit:     max requests per window
 *   X-RateLimit-Remaining: requests left in current window
 *   Retry-After:           seconds until next allowed request (on 429)
 */
public class RateLimiterHandler implements HttpHandler {

    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            String resource = extractResource(path);
            String clientId = extractParam(query, "clientId");

            if (resource == null || resource.isBlank()) {
                sendResponse(exchange, 400, errorJson("Use GET /api/{resource}?clientId={id}"));
                return;
            }
            if (clientId == null || clientId.isBlank()) {
                clientId = exchange.getRemoteAddress().getAddress().getHostAddress();
            }

            RateLimiterManager manager = RateLimiterManager.getInstance();
            RateLimitResult result;
            try {
                result = manager.tryAcquire(resource, clientId);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 404, errorJson("No rate limiter for resource: " + resource));
                return;
            }

            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(result.limit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(result.remaining()));

            if (result.allowed()) {
                JsonObject body = new JsonObject();
                body.addProperty("message", "Request allowed");
                body.addProperty("resource", resource);
                body.addProperty("clientId", clientId);
                body.addProperty("remaining", result.remaining());
                sendResponse(exchange, 200, GSON.toJson(body));
            } else {
                long retryAfterSec = Math.max(1, result.retryAfterMillis() / 1000);
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfterSec));

                JsonObject body = new JsonObject();
                body.addProperty("error", "Rate limit exceeded");
                body.addProperty("resource", resource);
                body.addProperty("clientId", clientId);
                body.addProperty("retryAfterMs", result.retryAfterMillis());
                sendResponse(exchange, 429, GSON.toJson(body));
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, errorJson("Internal error: " + e.getMessage()));
        }
    }

    private String extractResource(String path) {
        String prefix = "/api/";
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length());
        }
        return null;
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String errorJson(String msg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", msg);
        return GSON.toJson(obj);
    }
}
