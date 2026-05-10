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

### 2. The ReadLock Bug and Fix (Important Interview Talking Point)

#### What is `accessOrder`?

`LinkedHashMap` has two modes, controlled by the third constructor argument:

```java
new LinkedHashMap<>(capacity, loadFactor, accessOrder)
```

| `accessOrder` | Behavior | Internal ordering |
|---|---|---|
| `false` (default) | **Insertion order** — entries stay in the order they were `put()` | New entries go to tail. `get()` does NOT change order. |
| `true` | **Access order** — every `get()` or `put()` moves the entry to tail | Head is always the **least recently used** entry. |

Visual example with `accessOrder=true`:

```
Initial: put(A), put(B), put(C)
  Head → [A] ↔ [B] ↔ [C] ← Tail

After get(A):              ← A moves to tail (most recently used)
  Head → [B] ↔ [C] ↔ [A] ← Tail

Now B is LRU (head). If cache is full and we put(D):
  removeEldestEntry() returns true → B gets evicted
  Head → [C] ↔ [A] ↔ [D] ← Tail
```

This is what makes LinkedHashMap a one-liner LRU cache. But the catch is that **`get()` is no longer a read-only operation** — it relinks nodes internally.

#### The Bug (original code)

The original code had two get methods — `get()` with readLock (buggy) and `getWithPromotion()` with writeLock (correct):

```java
// ❌ BUG: LinkedHashMap.get() with accessOrder=true MUTATES the internal linked list
public Object get(String key) {
    lock.readLock().lock();     // <-- read lock allows concurrent access
    // ... cache.get(key) ...   // <-- but this MODIFIES the map's internal order!
}
```

`ReentrantReadWriteLock` allows **multiple threads to hold readLock simultaneously**. But with `accessOrder=true`, `cache.get()` relinks the accessed node's `prev`/`next` pointers in the internal doubly-linked list. Two threads doing this concurrently corrupt the list:

```
Thread 1 (readLock): cache.get("A")  → moves A's node to tail
Thread 2 (readLock): cache.get("B")  → moves B's node to tail
                                        ↑ BOTH mutating the same linked list concurrently!
```

This can cause:
- Corrupted linked list (broken prev/next pointers)
- Infinite loop when iterating
- `NullPointerException`
- Wrong entry evicted

#### The Fix (current code)

Merged into a single `get()` that always uses `writeLock`:

```java
// ✅ FIXED: writeLock because LinkedHashMap.get() mutates internal structure
public Object get(String key) {
    lock.writeLock().lock();    // <-- exclusive lock, safe
    try {
        CacheEntry<Object> entry = cache.get(key);
        // ...
    } finally {
        lock.writeLock().unlock();
    }
}
```

Also fixed `getEntryMetadata()` which had the same problem — now uses a readLock-first check with `containsKey()` (safe, doesn't mutate) and upgrades to writeLock only if the key exists.

#### The Trade-off (why this matters)

Now reads and writes **both** need exclusive access — you lose concurrent reads entirely. This is the fundamental downside of the LinkedHashMap approach:

```
LinkedHashMap approach:
  Thread 1 (get):  ──[acquire WRITE lock]──[get("A")]──[release]──
  Thread 2 (get):  ──────────[wait]──────[acquire WRITE lock]──[get("B")]──[release]──
  ↑ Reads are serialized. No concurrency benefit from ReadWriteLock.

Custom DLL approach (cache-manager module):
  Thread 1 (get):  ──[acquire READ lock]──[HashMap.get("A")]──[release]──
  Thread 2 (get):  ──[acquire READ lock]──[HashMap.get("B")]──[release]──  ← concurrent!
  ↑ True concurrent reads. HashMap.get() is a pure read.
```

**Bottom line for interviews**: "I know LinkedHashMap is a shortcut for LRU, but its `get()` with `accessOrder=true` mutates internal structure, making read-lock unsafe. With a custom DoublyLinkedList + HashMap, my `get()` is a pure read, allowing true concurrent readers."

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

| Issue | Status | Explanation |
|---|---|---|
| `get()` with readLock is unsafe | **Fixed** | LinkedHashMap.get() mutates with accessOrder=true. Now uses writeLock. See section above for full explanation. |
| `totalHits`/`totalMisses` not atomic | Open | Metrics counters are `long` (not `AtomicLong`). Now safe since `get()` uses writeLock too, but in production use `LongAdder` for lock-free counting. |
| RedisStub is not thread-safe | Open | The `HashMap` inside `RedisStub` has no synchronization. In production, replace with `StringRedisTemplate` which handles its own thread safety. |
| No TTL support | Open | Redis supports `SET key value EX 60`. This implementation doesn't. Could be added with a scheduled evictor thread. |

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

---

## Topics to Learn — Prerequisite Knowledge Used in Both Modules

### Data Structures (Core — must know)

| Topic | Where it's used | What to study |
|---|---|---|
| **HashMap** | Backbone of both LRU implementations | How hashing works, O(1) amortized lookup, hash collisions, load factor, rehashing |
| **Doubly Linked List** | `cache-manager`: custom `DoublyLinkedList` for LRU ordering | Node structure (prev/next pointers), O(1) insert/delete when you have a node reference, sentinel head/tail nodes |
| **LinkedHashMap** | `cache-manager-springboot`: used as a built-in LRU | How it extends HashMap with an internal doubly-linked list, `accessOrder` flag, `removeEldestEntry()` hook |

### Concurrency (Critical — frequently asked in interviews)

| Topic | Where it's used | What to study |
|---|---|---|
| **ReentrantReadWriteLock** | Both modules — thread-safe cache access | Read lock vs write lock, when to use which, lock downgrading/upgrading (not possible in Java), fairness policy |
| **Thread Safety** | `InMemoryCache`, `LRUCacheService` | Race conditions, critical sections, why `synchronized` is too coarse, why `ConcurrentHashMap` alone isn't enough for LRU |
| **ConcurrentHashMap** | `CacheManager` singleton registry | `computeIfAbsent()` for atomic creation, segment-level locking, why it's better than `Collections.synchronizedMap()` |
| **AtomicLong / LongAdder** | Metrics counters (production improvement) | CAS (compare-and-swap), lock-free data structures, `LongAdder` for high-contention counters |
| **CountDownLatch** | `LRUCacheServiceTest` concurrency tests | Coordinating thread completion, testing concurrent code |

### Design Patterns (LLD interview staples)

| Pattern | Where it's used | What to study |
|---|---|---|
| **Strategy** | `cache-manager`: `EvictionPolicy` interface with `LRUEvictionPolicy` | Define a family of algorithms, encapsulate each, make them interchangeable. Classic example: sorting strategies, payment methods |
| **Singleton** | `cache-manager`: `CacheManager.getInstance()` | Eager vs lazy initialization, double-checked locking, enum singleton, thread-safe creation |
| **Adapter** | Both: `RedisLikeClient` / `RedisLikeCache` wrapping the cache | Convert one interface to another. Here: cache interface → Redis-like interface |
| **Template Method** | `LinkedHashMap.removeEldestEntry()` | Superclass defines the algorithm skeleton, subclass overrides specific steps |

### Java Specifics

| Topic | Where it's used | What to study |
|---|---|---|
| **Generics** | `Cache<K,V>`, `CacheEntry<V>`, `EvictionPolicy<K>` | Type parameters, bounded types, type erasure, wildcard (`? extends`, `? super`) |
| **Optional** | `cache-manager`: return type for `get()` | Why Optional over null, `orElse()`, `map()`, `ifPresent()` — avoids NPE |
| **Anonymous inner class** | LinkedHashMap subclass with `removeEldestEntry()` | Inline subclassing, accessing enclosing class fields (`LRUCacheService.this.capacity`) |
| **try-with-resources** | HTTP request/response handling | `AutoCloseable`, resource cleanup guarantee |

### System Design (for follow-up discussions)

| Topic | Where it's used | What to study |
|---|---|---|
| **Caching Strategies** | Core problem | Cache-aside, read-through, write-through, write-behind |
| **Two-Level Cache (L1 + L2)** | `TwoLevelCacheService` | Local cache vs distributed cache, cache coherence, invalidation strategies |
| **LRU vs LFU vs FIFO** | Eviction policy choice | When to use which — LRU for temporal locality, LFU for frequency-based access, FIFO for simplicity |
| **Cache Invalidation** | Two-level cache write/delete paths | "The two hardest problems in CS" — TTL-based, event-based, pub/sub invalidation |
| **Consistent Hashing** | Distributing cache across nodes | How to shard keys, virtual nodes, handling node additions/removals |
| **Redis** | `RedisLikeCache`, `TwoLevelCacheService` | Data types (String, Hash, List, Set, Sorted Set), TTL, pub/sub, persistence (RDB, AOF), cluster mode |

### Spring Boot (if framework-based round)

| Topic | Where it's used | What to study |
|---|---|---|
| **Spring MVC** | `CacheController` with `@RestController` | `@GetMapping`, `@PutMapping`, `@DeleteMapping`, `@PathVariable`, `@RequestBody`, `ResponseEntity` |
| **Dependency Injection** | `@Service`, constructor injection | IoC container, `@Autowired`, why constructor injection over field injection |
| **Spring Caching** | Production alternative | `@Cacheable`, `@CacheEvict`, `@CachePut`, `CacheManager` interface, Caffeine + Redis integration |

### Suggested Study Order

```
Week 1: Data Structures
  → HashMap internals, Doubly Linked List, LinkedHashMap
  → Practice: Implement LRU Cache from scratch (LeetCode #146)

Week 2: Concurrency
  → synchronized → ReentrantLock → ReentrantReadWriteLock
  → ConcurrentHashMap, AtomicLong, CountDownLatch
  → Practice: Make your LRU cache thread-safe

Week 3: Design Patterns
  → Strategy, Singleton, Adapter, Template Method
  → Practice: Add LFU eviction to cache-manager using Strategy pattern

Week 4: System Design + Spring Boot
  → Caching strategies, L1/L2 cache, Redis basics
  → Spring MVC, Dependency Injection
  → Practice: Run cache-manager-springboot, test with curl
```
