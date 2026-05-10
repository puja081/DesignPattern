package com.cache.service;

/**
 * TwoLevelCacheService — L1 (in-memory LRU) + L2 (Redis) cache pattern.
 *
 * ─── SCENARIO B EXPLAINED ───────────────────────────────────────────────────
 * In a production microservices system (like Licious), you typically have:
 *
 *   L1 = In-process LRU cache (this JVM, microsecond latency)
 *   L2 = Redis cluster (shared across all service instances, millisecond latency)
 *   L3 = Database / upstream API (slowest, what we're trying to avoid)
 *
 * READ path:
 *   1. Check L1. Hit → return immediately (fastest).
 *   2. Miss → check L2 (Redis). Hit → populate L1, return.
 *   3. Miss → fetch from DB/API → populate L2, populate L1, return.
 *
 * WRITE path:
 *   1. Write to DB/source of truth.
 *   2. Invalidate / update L2 (Redis).
 *   3. Invalidate / update L1.
 *
 * ─── WHY THIS MATTERS IN INTERVIEWS ─────────────────────────────────────────
 * When an interviewer says "show redis.get(key) and redis.put(key, value)",
 * they may be testing whether you know:
 *   - Redis is NOT a replacement for your LRU cache — it's complementary
 *   - L1 gives ultra-low latency but is local; Redis provides shared state
 *   - Cache invalidation across L1 and L2 is the hard problem
 *
 * ─── NOTE ON THIS IMPLEMENTATION ────────────────────────────────────────────
 * This class shows the PATTERN without a real Redis dependency (no spring-data-redis).
 * In production, replace the RedisStub with:
 *   - @Autowired StringRedisTemplate redisTemplate;
 *   - redisTemplate.opsForValue().get(key)
 *   - redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10))
 *
 * ─── COMPLEXITY ──────────────────────────────────────────────────────────────
 *   L1 hit:  O(1) — in-process
 *   L2 hit:  O(1) — Redis network call (~1ms)
 *   Full miss: O(1) + DB/API latency
 */
public class TwoLevelCacheService {

    private final LRUCacheService l1Cache;   // local in-memory LRU
    private final RedisStub l2Cache;         // replace with StringRedisTemplate in prod

    public TwoLevelCacheService(int l1Capacity) {
        this.l1Cache = new LRUCacheService(l1Capacity);
        this.l2Cache = new RedisStub();
    }

    /**
     * Read with two-level fallback.
     */
    public String get(String key) {
        // ── L1 hit ──────────────────────────────────────────────────────────
        Object l1Value = l1Cache.get(key);
        if (l1Value != null) {
            System.out.println("[L1 HIT] key=" + key);
            return l1Value.toString();
        }

        // ── L2 hit (Redis) ───────────────────────────────────────────────────
        //   Production: String l2Value = redisTemplate.opsForValue().get(key);
        String l2Value = l2Cache.get(key);
        if (l2Value != null) {
            System.out.println("[L2 HIT] key=" + key + " — populating L1");
            l1Cache.put(key, l2Value);   // warm L1
            return l2Value;
        }

        // ── Full miss — caller responsible for DB fetch ──────────────────────
        System.out.println("[CACHE MISS] key=" + key + " — fetch from source required");
        return null;
    }

    /**
     * Write to both cache levels.
     */
    public void put(String key, String value) {
        // Production: redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10));
        l2Cache.set(key, value);   // L2 first (shared state)
        l1Cache.put(key, value);   // then L1
        System.out.println("[WRITE] key=" + key + " written to L1 + L2");
    }

    /**
     * Invalidate from both levels (critical for consistency).
     */
    public void invalidate(String key) {
        l1Cache.delete(key);
        l2Cache.del(key);
        System.out.println("[INVALIDATE] key=" + key + " removed from L1 + L2");
    }

    // ─── Redis stub — replace with real StringRedisTemplate ──────────────────
    static class RedisStub {
        private final java.util.Map<String, String> store = new java.util.HashMap<>();

        // Simulates: redisTemplate.opsForValue().get(key)
        public String get(String key) { return store.get(key); }

        // Simulates: redisTemplate.opsForValue().set(key, value, ttl)
        public void set(String key, String value) { store.put(key, value); }

        // Simulates: redisTemplate.delete(key)
        public void del(String key) { store.remove(key); }
    }
}
