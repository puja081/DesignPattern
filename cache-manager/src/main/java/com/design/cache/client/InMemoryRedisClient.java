package com.design.cache.client;

import com.design.cache.core.Cache;
import com.design.cache.core.CacheManager;

import java.util.Optional;

/**
 * Redis-like client backed by the in-memory LRU cache.
 * Provides the same redis.get(key) / redis.put(key, value) semantics
 * without an external Redis server.
 *
 * Usage:
 *   RedisLikeClient redis = new InMemoryRedisClient("my-store", 100);
 *   redis.put("user:1", "{\"name\":\"Alice\"}");
 *   Optional<String> val = redis.get("user:1");
 */
public class InMemoryRedisClient implements RedisLikeClient {

    private final Cache<String, String> cache;

    public InMemoryRedisClient(String namespace, int capacity) {
        this.cache = CacheManager.getInstance().getOrCreateCache(namespace, capacity);
    }

    @Override
    public Optional<String> get(String key) {
        return cache.get(key);
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
    }

    @Override
    public boolean del(String key) {
        return cache.remove(key);
    }

    @Override
    public void flushAll() {
        cache.clear();
    }

    @Override
    public int dbSize() {
        return cache.size();
    }
}
