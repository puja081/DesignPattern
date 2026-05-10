package com.cache.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RedisLikeCache — simulates redis.get(key) / redis.set(key, value) semantics
 * using the same LRU eviction engine under the hood.
 *
 * ─── WHY A SEPARATE CLASS? ──────────────────────────────────────────────────
 * The interview requirement mentions "redis.get(key) and redis.put(key, value)".
 * This likely refers to one of two scenarios:
 *
 *   SCENARIO A — "Show how your cache behaves like Redis":
 *     The interviewer wants a simplified Redis-style API with string keys/values,
 *     mimicking the Redis client interface (Jedis/Lettuce style).
 *     → This class satisfies that: redis.get(key), redis.set(key, value), redis.del(key)
 *
 *   SCENARIO B — "Show Redis integration alongside your custom cache":
 *     The system should use Redis as a distributed L2 cache and your in-memory
 *     map as an L1 cache. Cache reads check L1 first, fall back to Redis.
 *     → See the TwoLevelCacheService class for this pattern.
 *
 * This class is SCENARIO A. It wraps LRUCacheService with Redis-style method names.
 *
 * ─── REDIS CLIENT ANALOGY (Jedis) ───────────────────────────────────────────
 *   Jedis jedis = new Jedis("localhost", 6379);
 *   jedis.set("user:1", "Alice");   ← our: redis.set("user:1", "Alice")
 *   jedis.get("user:1");            ← our: redis.get("user:1")
 *   jedis.del("user:1");            ← our: redis.del("user:1")
 *   jedis.exists("user:1");         ← our: redis.exists("user:1")
 *
 * ─── COMPLEXITY ──────────────────────────────────────────────────────────────
 *   get():    O(1)
 *   set():    O(1), evicts LRU entry if at capacity
 *   del():    O(1)
 *   exists(): O(1)
 */
@Service
public class RedisLikeCache {

    private final LRUCacheService lruCache;

    public RedisLikeCache() {
        // Configurable capacity — default 100
        this.lruCache = new LRUCacheService(100);
    }

    public RedisLikeCache(int capacity) {
        this.lruCache = new LRUCacheService(capacity);
    }

    /**
     * redis.get(key)
     * Returns the string value, or null if key does not exist.
     * Promotes the key to MRU position (LRU behavior).
     */
    public String get(String key) {
        Object val = lruCache.get(key);
        return val == null ? null : val.toString();
    }

    /**
     * redis.set(key, value)
     * Stores the value. If cache is full, evicts least recently used entry.
     * Equivalent to Redis SET command (no TTL variant here for simplicity).
     */
    public void set(String key, String value) {
        lruCache.put(key, value);
    }

    /**
     * redis.del(key)
     * Deletes a key. Returns true if key existed.
     */
    public boolean del(String key) {
        return lruCache.delete(key);
    }

    /**
     * redis.exists(key)
     * Returns true if key is present in cache.
     */
    public boolean exists(String key) {
        return lruCache.containsKey(key);
    }

    /**
     * redis.flushAll()
     * Removes all keys from the cache.
     */
    public void flushAll() {
        lruCache.clear();
    }

    // Expose stats for monitoring
    public String info() {
        var stats = lruCache.getStats();
        return String.format(
            "size=%d, capacity=%d, hits=%d, misses=%d, evictions=%d, hitRatio=%.2f%%",
            stats.getCurrentSize(), stats.getMaxCapacity(),
            stats.getTotalHits(), stats.getTotalMisses(),
            stats.getTotalEvictions(), stats.getHitRatio() * 100
        );
    }
}
