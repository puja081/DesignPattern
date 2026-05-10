# Cache Manager (Spring Boot) — LRU + Two-Level Cache + Redis-like API

## Overview

This is the **Spring Boot** approach to the same cache interview problem. It uses `LinkedHashMap` for LRU instead of a custom doubly-linked list, runs on Spring MVC for the REST layer, and includes a **Two-Level Cache (L1 + L2)** pattern that the `cache-manager` module doesn't have.

---

## How to Run

```bash
# From the project root
./gradlew :cache-manager-springboot:bootRun

# Run tests
./gradlew :cache-manager-springboot:test
```

Server starts on **port 8081** (`application.properties`).

### REST Endpoints

```bash
# Store
curl -X PUT http://localhost:8081/api/cache/city -H "Content-Type: application/json" -d '{"value":"Bangalore"}'

# Fetch
curl http://localhost:8081/api/cache/city

# Delete
curl -X DELETE http://localhost:8081/api/cache/city

# List all keys
curl http://localhost:8081/api/cache

# Stats (hits, misses, evictions, hitRatio)
curl http://localhost:8081/api/cache/stats

# Entry metadata (createdAt, lastAccessedAt, accessCount)
curl http://localhost:8081/api/cache/city/metadata

# Clear all
curl -X DELETE http://localhost:8081/api/cache
```

---

## Architecture

```
┌────────────────────────────────────────────────────┐
│              CacheController (REST)                │
│  @RestController + @RequestMapping("/api/cache")   │
└──────────┬──────────────────────┬──────────────────┘
           │                      │
           ▼                      ▼
┌──────────────────┐   ┌─────────────────────────┐
│  LRUCacheService │   │   RedisLikeCache        │
│  (LinkedHashMap  │   │   redis.get(key)         │
│   accessOrder)   │   │   redis.set(key, value)  │
│                  │   │   (wraps LRUCacheService) │
│  + CacheEntry    │   └─────────────────────────┘
│  + CacheStats    │
│  + R/W Lock      │
└──────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│       TwoLevelCacheService           │
│  L1: LRUCacheService (in-process)    │
│  L2: RedisStub (simulated Redis)     │
│                                      │
│  Read:  L1 → L2 → DB (miss)         │
│  Write: L2 first → L1               │
│  Invalidate: L1 + L2                │
└──────────────────────────────────────┘
```

---

## File-by-File Breakdown

| File | What it does |
|---|---|
| `CacheEntry<V>` | Wraps value with metadata: `createdAt`, `lastAccessedAt`, `accessCount` |
| `CacheStats` | Snapshot of cache metrics: size, capacity, hits, misses, evictions, hitRatio |
| `LRUCacheService` | Core LRU cache using `LinkedHashMap(capacity, 0.75f, true)` + `ReentrantReadWriteLock` |
| `RedisLikeCache` | Thin wrapper over `LRUCacheService` exposing `redis.get()`, `redis.set()`, `redis.del()` |
| `TwoLevelCacheService` | L1 (in-memory) + L2 (Redis stub) two-level cache pattern |
| `CacheController` | Spring MVC REST endpoints with proper HTTP status codes |
| `LRUCacheServiceTest` | JUnit 5 tests including concurrency tests |

---

## Key Concepts in This Implementation

### 1. LinkedHashMap with `accessOrder=true`

```java
this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<...> eldest) {
        return size() > capacity;
    }
};
```

- Third constructor argument `true` means **access-ordered** (not insertion-ordered).
- Every `get()` call moves the entry to the tail internally.
- `removeEldestEntry()` is called after every `put()` — returns `true` to auto-evict the head (LRU).

### 2. The ReadLock Bug (Important Interview Talking Point)

The code has **two get methods** — `get()` and `getWithPromotion()`. This is intentional:

```java
// ❌ BUG: LinkedHashMap.get() with accessOrder=true MUTATES the internal linked list
public Object get(String key) {
    lock.readLock().lock();     // <-- read lock allows concurrent access
    // ... cache.get(key) ...   // <-- but this MODIFIES the map's internal order!
}

// ✅ CORRECT: must use write lock when map's get() mutates structure
public Object getWithPromotion(String key) {
    lock.writeLock().lock();    // <-- exclusive lock, safe
    // ... cache.get(key) ...
}
```

This is a **classic interview catch**: `LinkedHashMap.get()` with `accessOrder=true` is NOT a read-only operation — it relinks nodes internally. Using `readLock` is unsafe here. The code highlights this with comments and provides both versions so you can discuss the trade-off.

### 3. Two-Level Cache (L1 + L2)

This is the most interesting addition. The `TwoLevelCacheService` shows:

```
READ:   Client → L1 (in-memory, μs) → L2 (Redis, ~1ms) → DB (slow)
WRITE:  Client → L2 (Redis) → L1 (in-memory)
DELETE: Client → L1 + L2 (both invalidated)
```

**Why L2 before L1 on writes?** Redis is the shared state across all instances of a service. If you write L1 first and crash before L2, other instances see stale data.

### 4. CacheEntry Metadata

Unlike the plain Java module, each entry tracks:
- `createdAt` — when the entry was first inserted
- `lastAccessedAt` — updated on every `get()`
- `accessCount` — how many times this key has been read

This is exposed via `GET /api/cache/{key}/metadata`.

---

## Known Issues / Interview Discussion Points

| Issue | Explanation |
|---|---|
| `get()` with readLock is unsafe | LinkedHashMap.get() mutates with accessOrder=true. The code includes both versions to discuss this. |
| `totalHits`/`totalMisses` not atomic | Metrics counters are `long` (not `AtomicLong`). Under write lock this is fine, but `get()` under read lock can have races. In production, use `LongAdder`. |
| RedisStub is not thread-safe | The `HashMap` inside `RedisStub` has no synchronization. In production, this is replaced by `StringRedisTemplate` which handles its own thread safety. |
| No TTL support | Redis supports `SET key value EX 60`. This implementation doesn't. Could be added with a scheduled evictor thread. |

---

# Comparison: `cache-manager` vs `cache-manager-springboot`

## At a Glance

| Aspect | `cache-manager` (Plain Java) | `cache-manager-springboot` (This module) |
|---|---|---|
| **Framework** | None — plain Java + JDK HttpServer | Spring Boot + Spring MVC |
| **LRU Data Structure** | Custom `HashMap` + `DoublyLinkedList` | `LinkedHashMap(accessOrder=true)` |
| **Eviction Design** | Strategy pattern (`EvictionPolicy` interface) | `removeEldestEntry()` override |
| **Thread Safety** | `ReentrantReadWriteLock` | `ReentrantReadWriteLock` (same) |
| **Cache Manager** | Singleton with named caches (`ConcurrentHashMap`) | Single `@Service` bean |
| **Redis-like API** | `RedisLikeClient` interface + `InMemoryRedisClient` | `RedisLikeCache` wrapping `LRUCacheService` |
| **Two-Level Cache** | Not included | `TwoLevelCacheService` (L1 + L2) |
| **Entry Metadata** | Not included | `CacheEntry` with createdAt, accessCount |
| **Cache Stats** | Basic size/capacity | Hits, misses, evictions, hitRatio |
| **Unit Tests** | Not included | JUnit 5 with concurrency tests |
| **REST Endpoints** | 5 endpoints | 7 endpoints (+ metadata, list keys) |

---

## Which to Use When?

### Use `cache-manager` (Plain Java) when:

- **Machine coding round says "no frameworks"** — many companies explicitly ban Spring Boot.
- **You want to show you understand the internals** — building HashMap + DoublyLinkedList from scratch demonstrates you know *how* LRU works, not just *that* it works.
- **The interviewer asks about design patterns** — Strategy pattern for eviction is a strong talking point.
- **Time is limited** — no Spring Boot setup overhead; runs with just `java Main.java`.

### Use `cache-manager-springboot` (This module) when:

- **The round allows/expects frameworks** — Spring Boot is the standard for Java microservices.
- **The interviewer asks about production readiness** — Spring DI, proper REST controllers, `@Service` annotations show production patterns.
- **They ask about "redis.get / redis.put" specifically** — this module has both interpretations:
  - **Scenario A**: `RedisLikeCache` — Redis-style API over local cache.
  - **Scenario B**: `TwoLevelCacheService` — L1 (local) + L2 (Redis) layered cache.
- **They want to see tests** — JUnit 5 tests with concurrency checks are included.

---

## Deep Dive: Data Structure Trade-off

### Custom DoublyLinkedList + HashMap (cache-manager)

```
  Pros:
  ✓ Shows you understand the O(1) mechanics
  ✓ Read lock is SAFE — get() doesn't mutate the HashMap
  ✓ Full control over eviction behavior
  ✓ Strategy pattern makes adding LFU/FIFO trivial
  
  Cons:
  ✗ More code to write (DoublyLinkedList, Node class)
  ✗ More surface area for bugs
```

### LinkedHashMap with accessOrder=true (cache-manager-springboot)

```
  Pros:
  ✓ Less code — Java stdlib does the heavy lifting
  ✓ removeEldestEntry() makes eviction a one-liner
  ✓ Battle-tested JDK implementation
  
  Cons:
  ✗ get() MUTATES internal structure — read lock is UNSAFE
  ✗ Must use write lock even for reads (hurts concurrency)
  ✗ Harder to extend to LFU/FIFO (no strategy pattern)
  ✗ Interviewer may say "show me you understand the internals"
```

**Bottom line**: The custom DLL approach is better for demonstrating knowledge. The LinkedHashMap approach is better for demonstrating pragmatism and production speed.

---

## Deep Dive: Two-Level Cache (Unique to This Module)

This is the biggest differentiator. In production microservices (like Licious):

```
                    ┌──────────────┐
                    │   Service A  │──── L1 (in-memory)
                    └──────┬───────┘
                           │
   ┌──────────────┐        │        ┌──────────────┐
   │   Service B  │────────┼────────│   Service C  │
   │  L1 (local)  │        │        │  L1 (local)  │
   └──────────────┘        │        └──────────────┘
                           ▼
                    ┌──────────────┐
                    │    Redis     │  ← L2 (shared)
                    │   Cluster    │
                    └──────┬───────┘
                           │
                    ┌──────────────┐
                    │   Database   │  ← Source of truth
                    └──────────────┘
```

- **L1** is per-JVM, microsecond access, but each instance has its own copy.
- **L2** (Redis) is shared, millisecond access, all instances see the same data.
- **Invalidation** is the hard part — when data changes, you must invalidate both levels.

---

## How to Talk About This in an Interview

1. **Start with**: "I'll use HashMap + DoublyLinkedList for O(1) LRU — let me show the mechanics."
2. **If they ask "why not LinkedHashMap?"**: "I could, but LinkedHashMap.get() with accessOrder=true mutates internal structure, making read-lock unsafe. With a custom DLL, my get() is a pure read."
3. **If they ask about Redis**: "There are two interpretations — a Redis-like API abstraction over local cache, or a two-level L1/L2 cache where Redis is the shared L2. Let me show both."
4. **If they ask about production**: "In production I'd use Spring Boot with `@Cacheable`, backed by Caffeine for L1 and Redis for L2, with `spring-data-redis` for the L2 layer."
