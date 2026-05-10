package com.design.cache.core;

import com.design.cache.eviction.LRUEvictionPolicy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Singleton cache manager that creates and retrieves named cache instances.
 * Each named cache has its own capacity and eviction policy.
 *
 * Thread-safe: uses ConcurrentHashMap for the registry and
 * computeIfAbsent for atomic cache creation.
 */
public final class CacheManager {

    private static final CacheManager INSTANCE = new CacheManager();
    private static final int DEFAULT_CAPACITY = 256;

    private final ConcurrentMap<String, Cache<String, String>> caches = new ConcurrentHashMap<>();

    private CacheManager() {}

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    public Cache<String, String> getOrCreateCache(String name) {
        return getOrCreateCache(name, DEFAULT_CAPACITY);
    }

    public Cache<String, String> getOrCreateCache(String name, int capacity) {
        return caches.computeIfAbsent(name,
                k -> new InMemoryCache<>(capacity, new LRUEvictionPolicy<>()));
    }

    public Cache<String, String> getCache(String name) {
        return caches.get(name);
    }

    public boolean removeCache(String name) {
        Cache<String, String> removed = caches.remove(name);
        if (removed != null) {
            removed.clear();
            return true;
        }
        return false;
    }
}
