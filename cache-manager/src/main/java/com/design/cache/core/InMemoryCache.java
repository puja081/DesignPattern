package com.design.cache.core;

import com.design.cache.eviction.EvictionPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, finite-capacity in-memory cache.
 *
 * Uses a ReentrantReadWriteLock so multiple threads can read concurrently
 * while writes (put / remove / evict) hold exclusive access.
 * The eviction strategy is pluggable via the {@link EvictionPolicy} interface.
 */
public class InMemoryCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, V> store;
    private final EvictionPolicy<K> evictionPolicy;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryCache(int capacity, EvictionPolicy<K> evictionPolicy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }
        this.capacity = capacity;
        this.store = new HashMap<>(capacity);
        this.evictionPolicy = evictionPolicy;
    }

    @Override
    public Optional<V> get(K key) {
        lock.readLock().lock();
        try {
            V value = store.get(key);
            if (value != null) {
                evictionPolicy.onAccess(key);
                return Optional.of(value);
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            if (store.containsKey(key)) {
                store.put(key, value);
                evictionPolicy.onAccess(key);
                return;
            }

            if (store.size() >= capacity) {
                K evictedKey = evictionPolicy.evict();
                if (evictedKey != null) {
                    store.remove(evictedKey);
                    System.out.println("[Cache] Evicted key: " + evictedKey);
                }
            }

            store.put(key, value);
            evictionPolicy.onInsert(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.writeLock().lock();
        try {
            if (store.containsKey(key)) {
                store.remove(key);
                evictionPolicy.onRemove(key);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            for (K key : store.keySet()) {
                evictionPolicy.onRemove(key);
            }
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
