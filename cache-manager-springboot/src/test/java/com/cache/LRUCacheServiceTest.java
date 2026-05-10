package com.cache;

import com.cache.service.LRUCacheService;
import com.cache.model.CacheStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering:
 *  1. Basic put/get
 *  2. LRU eviction correctness
 *  3. Update (no phantom eviction)
 *  4. Delete
 *  5. Stats accuracy
 *  6. Concurrent access (thread safety)
 */
public class LRUCacheServiceTest {

    private LRUCacheService cache;

    @BeforeEach
    void setUp() {
        cache = new LRUCacheService(3); // small capacity for easy eviction testing
    }

    // ─── Basic Operations ─────────────────────────────────────────────────────

    @Test
    @DisplayName("put and get basic operation")
    void testPutAndGet() {
        cache.put("name", "Alice");
        assertEquals("Alice", cache.get("name"));
    }

    @Test
    @DisplayName("get on missing key returns null")
    void testGetMissingKey() {
        assertNull(cache.get("ghost"));
    }

    @Test
    @DisplayName("update existing key does not grow cache size")
    void testUpdateDoesNotGrow() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");
        cache.put("k1", "v1_updated"); // update, not insert
        assertEquals(3, cache.getCurrentSize());
        assertEquals("v1_updated", cache.get("k1"));
    }

    // ─── LRU Eviction ────────────────────────────────────────────────────────

    @Test
    @DisplayName("LRU eviction: least recently used entry is evicted when full")
    void testLRUEviction() {
        // Fill cache: k1(LRU) → k2 → k3(MRU)
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        // Access k1, making k2 the LRU
        cache.get("k1"); // order now: k2(LRU) → k3 → k1(MRU)

        // Insert k4: should evict k2 (LRU)
        cache.put("k4", "v4");

        assertNull(cache.get("k2"),  "k2 should be evicted (LRU)");
        assertNotNull(cache.get("k1"), "k1 should survive");
        assertNotNull(cache.get("k3"), "k3 should survive");
        assertNotNull(cache.get("k4"), "k4 should be present");
    }

    @Test
    @DisplayName("LRU eviction increments eviction counter in stats")
    void testEvictionCountInStats() {
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");
        cache.put("k4", "v4"); // triggers eviction

        CacheStats stats = cache.getStats();
        assertEquals(1, stats.getTotalEvictions());
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete removes entry and returns true")
    void testDelete() {
        cache.put("k1", "v1");
        assertTrue(cache.delete("k1"));
        assertNull(cache.get("k1"));
    }

    @Test
    @DisplayName("delete on missing key returns false")
    void testDeleteMissing() {
        assertFalse(cache.delete("nonexistent"));
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hit/miss ratio tracked correctly")
    void testHitMissStats() {
        cache.put("k1", "v1");
        cache.get("k1"); // hit
        cache.get("k1"); // hit
        cache.get("missing"); // miss

        CacheStats stats = cache.getStats();
        assertEquals(2, stats.getTotalHits());
        assertEquals(1, stats.getTotalMisses());
        assertEquals(2.0 / 3.0, stats.getHitRatio(), 0.001);
    }

    // ─── Thread Safety ────────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent writes do not corrupt cache state")
    void testConcurrentWrites() throws InterruptedException {
        LRUCacheService bigCache = new LRUCacheService(1000);
        int threadCount = 20;
        int writesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < writesPerThread; i++) {
                    bigCache.put("thread-" + threadId + "-key-" + i, "value-" + i);
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        // Cache should have exactly threadCount * writesPerThread entries (≤ capacity)
        assertTrue(bigCache.getCurrentSize() <= 1000);
        assertTrue(bigCache.getCurrentSize() > 0);
    }

    @Test
    @DisplayName("concurrent reads and writes maintain consistency")
    void testConcurrentReadWrite() throws InterruptedException {
        cache = new LRUCacheService(50);
        cache.put("shared", "initial");

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger nullReads = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final boolean isWriter = t % 3 == 0;
            executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    if (isWriter) {
                        cache.put("shared", "updated-" + i);
                    } else {
                        Object val = cache.get("shared");
                        // shared key always exists (writers update, never delete it)
                        if (val == null) nullReads.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        assertEquals(0, nullReads.get(), "Shared key should never read as null");
    }
}
