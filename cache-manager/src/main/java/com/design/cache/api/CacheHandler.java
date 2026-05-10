package com.design.cache.api;

import com.design.cache.core.Cache;
import com.design.cache.core.CacheManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * HTTP handler for the cache REST API.
 *
 * Endpoints:
 *   GET    /cache/{key}          - fetch a value
 *   PUT    /cache/{key}          - store a value  (body: {"value": "..."})
 *   DELETE /cache/{key}          - remove a key
 *   DELETE /cache                - clear the entire cache
 *   GET    /cache?action=stats   - return cache size / capacity
 */
public class CacheHandler implements HttpHandler {

    private static final Gson GSON = new Gson();
    private static final String CACHE_NAME = "default";
    private static final int CACHE_CAPACITY = 1024;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();

            String key = extractKey(path);

            switch (method) {
                case "GET" -> handleGet(exchange, key, query);
                case "PUT" -> handlePut(exchange, key);
                case "DELETE" -> handleDelete(exchange, key);
                default -> sendResponse(exchange, 405, errorJson("Method not allowed"));
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, errorJson("Internal error: " + e.getMessage()));
        }
    }

    private void handleGet(HttpExchange exchange, String key, String query) throws IOException {
        if (key == null || key.isBlank()) {
            if ("action=stats".equals(query)) {
                Cache<String, String> cache = getCache();
                JsonObject stats = new JsonObject();
                stats.addProperty("size", cache.size());
                stats.addProperty("capacity", cache.capacity());
                sendResponse(exchange, 200, GSON.toJson(stats));
            } else {
                sendResponse(exchange, 400, errorJson("Key is required. Use GET /cache/{key}"));
            }
            return;
        }

        Cache<String, String> cache = getCache();
        Optional<String> value = cache.get(key);

        if (value.isPresent()) {
            JsonObject result = new JsonObject();
            result.addProperty("key", key);
            result.addProperty("value", value.get());
            sendResponse(exchange, 200, GSON.toJson(result));
        } else {
            sendResponse(exchange, 404, errorJson("Key not found: " + key));
        }
    }

    private void handlePut(HttpExchange exchange, String key) throws IOException {
        if (key == null || key.isBlank()) {
            sendResponse(exchange, 400, errorJson("Key is required. Use PUT /cache/{key}"));
            return;
        }

        String body = readBody(exchange);
        JsonObject json = GSON.fromJson(body, JsonObject.class);

        if (json == null || !json.has("value")) {
            sendResponse(exchange, 400, errorJson("Request body must contain 'value' field"));
            return;
        }

        String value = json.get("value").getAsString();
        Cache<String, String> cache = getCache();
        cache.put(key, value);

        JsonObject result = new JsonObject();
        result.addProperty("key", key);
        result.addProperty("value", value);
        result.addProperty("message", "Stored successfully");
        sendResponse(exchange, 200, GSON.toJson(result));
    }

    private void handleDelete(HttpExchange exchange, String key) throws IOException {
        Cache<String, String> cache = getCache();

        if (key == null || key.isBlank()) {
            cache.clear();
            sendResponse(exchange, 200, successJson("Cache cleared"));
            return;
        }

        boolean removed = cache.remove(key);
        if (removed) {
            sendResponse(exchange, 200, successJson("Key removed: " + key));
        } else {
            sendResponse(exchange, 404, errorJson("Key not found: " + key));
        }
    }

    private Cache<String, String> getCache() {
        return CacheManager.getInstance().getOrCreateCache(CACHE_NAME, CACHE_CAPACITY);
    }

    private String extractKey(String path) {
        String prefix = "/cache/";
        if (path.startsWith(prefix) && path.length() > prefix.length()) {
            return path.substring(prefix.length());
        }
        return null;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return GSON.toJson(obj);
    }

    private String successJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("message", message);
        return GSON.toJson(obj);
    }
}
