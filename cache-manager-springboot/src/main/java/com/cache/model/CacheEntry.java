package com.cache.model;

import java.time.Instant;

/**
 * Wraps a cached value with metadata: creation time, last access time, access count.
 * Used by LRUCacheService to track per-entry statistics.
 */
public class CacheEntry<V> {

    private final String key;
    private V value;
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private long accessCount;

    public CacheEntry(String key, V value) {
        this.key = key;
        this.value = value;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.accessCount = 0;
    }

    public void recordAccess() {
        this.lastAccessedAt = Instant.now();
        this.accessCount++;
    }

    public String getKey() { return key; }
    public V getValue() { return value; }
    public void setValue(V value) { this.value = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public long getAccessCount() { return accessCount; }
}
