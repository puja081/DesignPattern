package com.design.cache.eviction;

/**
 * Strategy interface for cache eviction.
 * Implementations decide which key to evict when the cache is full
 * and track access patterns to inform eviction decisions.
 */
public interface EvictionPolicy<K> {

    void onAccess(K key);

    void onInsert(K key);

    void onRemove(K key);

    K evict();
}
