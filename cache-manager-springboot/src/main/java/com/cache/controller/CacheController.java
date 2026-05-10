package com.cache.controller;

import com.cache.model.CacheEntry;
import com.cache.model.CacheStats;
import com.cache.service.LRUCacheService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RESTful API for the LRU Cache Manager.
 *
 * ENDPOINTS:
 *   GET    /api/cache/{key}           → fetch value (promotes LRU)
 *   PUT    /api/cache/{key}           → insert or update
 *   DELETE /api/cache/{key}           → remove entry
 *   GET    /api/cache                 → list all keys
 *   DELETE /api/cache                 → clear entire cache
 *   GET    /api/cache/stats           → hit ratio, evictions, size
 *   GET    /api/cache/{key}/metadata  → entry metadata (created, accessed, count)
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final LRUCacheService cacheService;

    public CacheController(LRUCacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ─── READ ────────────────────────────────────────────────────────────────

    /**
     * GET /api/cache/{key}
     * Fetches a value. Promotes the key to MRU position.
     * Returns 200 with value, or 404 if key not found.
     */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) {
        Object value = cacheService.get(key);
        if (value == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Key not found: " + key));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/cache
     * Returns all keys currently in cache (LRU → MRU order).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllKeys() {
        List<String> keys = cacheService.getAllKeys();
        return ResponseEntity.ok(Map.of(
                "keys", keys,
                "size", keys.size(),
                "capacity", cacheService.getCapacity()
        ));
    }

    /**
     * GET /api/cache/stats
     * Returns cache statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<CacheStats> getStats() {
        return ResponseEntity.ok(cacheService.getStats());
    }

    /**
     * GET /api/cache/{key}/metadata
     * Returns entry-level metadata without promoting LRU order.
     */
    @GetMapping("/{key}/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata(@PathVariable String key) {
        CacheEntry<Object> entry = cacheService.getEntryMetadata(key);
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Key not found: " + key));
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("key", entry.getKey());
        meta.put("createdAt", entry.getCreatedAt().toString());
        meta.put("lastAccessedAt", entry.getLastAccessedAt().toString());
        meta.put("accessCount", entry.getAccessCount());
        return ResponseEntity.ok(meta);
    }

    // ─── WRITE ───────────────────────────────────────────────────────────────

    /**
     * PUT /api/cache/{key}
     * Inserts or updates a key-value pair.
     * If cache is at capacity, LRU entry is evicted automatically.
     *
     * Request body: { "value": <any JSON value> }
     */
    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> put(
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        if (!body.containsKey("value")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Request body must contain 'value' field"));
        }
        Object value = body.get("value");
        boolean isUpdate = cacheService.containsKey(key);
        cacheService.put(key, value);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "value", value,
                "operation", isUpdate ? "updated" : "inserted",
                "cacheSize", cacheService.getCurrentSize(),
                "capacity", cacheService.getCapacity()
        ));
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /**
     * DELETE /api/cache/{key}
     * Removes a single entry. Returns 404 if not found.
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String key) {
        boolean removed = cacheService.delete(key);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Key not found: " + key));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Key deleted successfully",
                "key", key
        ));
    }

    /**
     * DELETE /api/cache
     * Clears the entire cache.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAll() {
        int sizeBefore = cacheService.getCurrentSize();
        cacheService.clear();
        return ResponseEntity.ok(Map.of(
                "message", "Cache cleared",
                "entriesRemoved", sizeBefore
        ));
    }
}
