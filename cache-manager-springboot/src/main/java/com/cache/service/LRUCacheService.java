package com.cache.service;

import com.cache.model.CacheEntry;
import com.cache.model.CacheStats;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe LRU Cache implementation using LinkedHashMap.
 *
 * DATA STRUCTURE CHOICE:
 * - LinkedHashMap with accessOrder=true gives us O(1) get, put, and eviction.
 *   It maintains a doubly-linked list internally, keeping insertion/access order.
 * - ReentrantReadWriteLock: allows multiple concurrent reads but exclusive writes,
 *   which is ideal for a read-heavy cache workload.
 *
 * LRU EVICTION:
 * - LinkedHashMap(capacity, loadFactor, true) → access-ordered map
 * - Override removeEldestEntry() to auto-evict the least recently used entry
 *   when size exceeds capacity.
 *
 * TIME COMPLEXITY:
 * - get():    O(1) amortized
 * - put():    O(1) amortized
 * - delete(): O(1) amortized
 */
@Service
public class LRUCacheService {

    private final int capacity;
    private final Map<String, CacheEntry<Object>> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Metrics
    private long totalHits = 0;
    private long totalMisses = 0;
    private long totalEvictions = 0;

    public LRUCacheService() {
        // Default capacity — in production, inject via @Value from application.properties
        this(100);
    }

    public LRUCacheService(int capacity) {
        this.capacity = capacity;
        // accessOrder=true: get() moves entry to tail (most recently used position)
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<Object>> eldest) {
                boolean shouldEvict = size() > LRUCacheService.this.capacity;
                if (shouldEvict) {
                    totalEvictions++;
                }
                return shouldEvict;
            }
        };
    }

    /**
     * Retrieve a value by key. Returns null if not found.
     *
     * WHY writeLock and not readLock?
     * LinkedHashMap with accessOrder=true relinks the accessed node to the
     * tail of its internal doubly-linked list on EVERY get() call.
     * That is a structural mutation — two concurrent get() calls can corrupt
     * the linked list (broken prev/next pointers → infinite loop or NPE).
     * So even "reads" need the exclusive write lock here.
     *
     * This is the fundamental trade-off of using LinkedHashMap for LRU:
     * you lose true concurrent reads. The custom DoublyLinkedList approach
     * in cache-manager avoids this because its HashMap.get() is a pure read.
     */
    public Object get(String key) {
        lock.writeLock().lock();
        try {
            CacheEntry<Object> entry = cache.get(key);
            if (entry == null) {
                totalMisses++;
                return null;
            }
            totalHits++;
            entry.recordAccess();
            return entry.getValue();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Insert or update a key-value pair.
     * If cache is full, the LRU entry is automatically evicted.
     */
    public void put(String key, Object value) {
        lock.writeLock().lock();
        try {
            if (cache.containsKey(key)) {
                cache.get(key).setValue(value); // update in place, access order updated
            } else {
                cache.put(key, new CacheEntry<>(key, value));
                // removeEldestEntry fires automatically if size > capacity
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete an entry by key.
     */
    public boolean delete(String key) {
        lock.writeLock().lock();
        try {
            return cache.remove(key) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check existence without promoting LRU order.
     */
    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            return cache.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all entries.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Return all keys (snapshot). Order: LRU → MRU (head → tail of LinkedHashMap).
     */
    public List<String> getAllKeys() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(cache.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Metadata for a specific cache entry.
     * Uses containsKey() (safe under readLock) to check existence,
     * then upgrades to writeLock only if the key exists, to avoid
     * the LinkedHashMap.get() mutation under readLock.
     */
    public CacheEntry<Object> getEntryMetadata(String key) {
        lock.readLock().lock();
        try {
            if (!cache.containsKey(key)) {
                return null;
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            return cache.get(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cache statistics: size, hit ratio, evictions.
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            return new CacheStats(cache.size(), capacity, totalHits, totalMisses, totalEvictions);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getCapacity() { return capacity; }
    public int getCurrentSize() {
        lock.readLock().lock();
        try { return cache.size(); } finally { lock.readLock().unlock(); }
    }
}
