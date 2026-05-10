# Cache Manager — In-Memory LRU Cache with REST API

## Problem Statement

Design and implement an in-memory cache manager with finite memory that:

1. Exposes **RESTful APIs** to persist and fetch cached data.
2. Evicts entries using **LRU (Least Recently Used)** when the cache is full.
3. Supports **high availability** — concurrent reads/writes with data consistency.
4. Provides a **Redis-like client interface** (`redis.get(key)` / `redis.put(key, value)`).

---

## Solution Architecture

```
┌──────────────────────────────────────────────────────────┐
│                        Main.java                         │
│              (demonstrates both interfaces)              │
└──────────┬──────────────────────────────┬────────────────┘
           │                              │
           ▼                              ▼
┌─────────────────────┐     ┌──────────────────────────┐
│   REST API Layer     │     │   Redis-like Client      │
│  (CacheServer +      │     │  (InMemoryRedisClient)   │
│   CacheHandler)      │     │                          │
│                      │     │  redis.get(key)           │
│  GET  /cache/{key}   │     │  redis.put(key, value)    │
│  PUT  /cache/{key}   │     │  redis.del(key)           │
│  DELETE /cache/{key} │     │  redis.flushAll()         │
└──────────┬───────────┘     └──────────┬───────────────┘
           │                            │
           └────────────┬───────────────┘
                        ▼
           ┌────────────────────────┐
           │    CacheManager        │
           │    (Singleton)         │
           │    Named cache registry│
           └────────────┬───────────┘
                        ▼
           ┌────────────────────────┐
           │   InMemoryCache<K,V>   │
           │   (Thread-safe)        │
           │                        │
           │  HashMap + R/W Lock    │
           └────────────┬───────────┘
                        ▼
           ┌────────────────────────┐
           │  EvictionPolicy<K>     │  ◄── Strategy Pattern
           │  └─ LRUEvictionPolicy  │
           │     DoublyLinkedList   │
           │     + HashMap          │
           └────────────────────────┘
```

---

## Key Design Decisions

### 1. LRU Cache — O(1) for all operations

The LRU cache uses a **HashMap + Doubly Linked List** combination:

| Operation | Time Complexity |
|-----------|----------------|
| `get(key)`   | O(1) — HashMap lookup + move node to tail |
| `put(key, value)` | O(1) — HashMap insert + add node to tail |
| `evict()`    | O(1) — remove head node (least recently used) |

**How it works:**
- The **DoublyLinkedList** maintains access order: head = LRU, tail = MRU.
- The **HashMap** maps keys to list nodes for O(1) node lookups.
- On `get()`, the accessed node is moved to the tail.
- On `put()` when full, the head node (LRU) is evicted.

```
  Head (LRU)                              Tail (MRU)
  ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐
  │ A │◄──►│ B │◄──►│ C │◄──►│ D │◄──►│ E │
  └───┘    └───┘    └───┘    └───┘    └───┘
    ▲                                    ▲
    │                                    │
  evict()                          recently accessed
```

### 2. Strategy Pattern for Eviction Policy

The eviction logic is decoupled from the cache via the `EvictionPolicy<K>` interface:

```java
public interface EvictionPolicy<K> {
    void onAccess(K key);   // called when a key is read
    void onInsert(K key);   // called when a new key is added
    void onRemove(K key);   // called when a key is explicitly removed
    K evict();              // returns the key to evict
}
```

This makes it trivial to add other strategies (LFU, FIFO, TTL-based) without modifying `InMemoryCache`.

### 3. Thread Safety — ReentrantReadWriteLock

Instead of `synchronized` (which blocks all threads), `InMemoryCache` uses a `ReentrantReadWriteLock`:

- **Read lock** — multiple threads can read concurrently (high availability for reads).
- **Write lock** — exclusive access for `put()`, `remove()`, `evict()` (data consistency).

This is the optimal choice for a **read-heavy** cache workload (which caches typically are).

### 4. Singleton CacheManager

`CacheManager` uses eager initialization (`static final INSTANCE`) and a `ConcurrentHashMap` for the registry, making cache creation/lookup thread-safe without explicit synchronization.

---

## Class-by-Class Breakdown

| Class | Responsibility |
|-------|---------------|
| `EvictionPolicy<K>` | Strategy interface — defines eviction contract |
| `LRUEvictionPolicy<K>` | LRU implementation using DoublyLinkedList + HashMap |
| `DoublyLinkedList<K>` | O(1) add/remove/move operations for access-order tracking |
| `Cache<K,V>` | Core cache interface |
| `InMemoryCache<K,V>` | Thread-safe cache with pluggable eviction; uses R/W lock |
| `CacheManager` | Singleton registry of named cache instances |
| `CacheHandler` | HTTP request handler (parses REST requests, calls cache) |
| `CacheServer` | Sets up Java's built-in HttpServer with a thread pool |
| `RedisLikeClient` | Interface mimicking Redis commands |
| `InMemoryRedisClient` | Implementation backed by the in-memory LRU cache |

---

## Part 1 — Redis-like Client (`redis.get` / `redis.put`)

The `RedisLikeClient` interface provides a familiar Redis API:

```java
RedisLikeClient redis = new InMemoryRedisClient("my-store", 100);

redis.put("user:1", "Alice");           // SET user:1 "Alice"
Optional<String> val = redis.get("user:1");  // GET user:1  → "Alice"
redis.del("user:1");                    // DEL user:1
redis.flushAll();                       // FLUSHALL
int size = redis.dbSize();              // DBSIZE
```

**Why this design?**
- The `RedisLikeClient` interface acts as an **Adapter** — callers code against it, and swapping the backing implementation (in-memory → real Redis via Jedis/Lettuce) requires zero caller changes.
- This is exactly how production services abstract cache providers.

---

## Part 2 — REST API

### Endpoints

| Method | Path | Body | Description |
|--------|------|------|-------------|
| `PUT` | `/cache/{key}` | `{"value": "..."}` | Store a key-value pair |
| `GET` | `/cache/{key}` | — | Retrieve a value by key |
| `DELETE` | `/cache/{key}` | — | Remove a specific key |
| `DELETE` | `/cache` | — | Clear the entire cache |
| `GET` | `/cache?action=stats` | — | Get cache size and capacity |

### Example curl commands

```bash
# Store a value
curl -X PUT http://localhost:8080/cache/city -d '{"value":"Bangalore"}'
# → {"key":"city","value":"Bangalore","message":"Stored successfully"}

# Retrieve a value
curl http://localhost:8080/cache/city
# → {"key":"city","value":"Bangalore"}

# Delete a key
curl -X DELETE http://localhost:8080/cache/city
# → {"message":"Key removed: city"}

# Cache stats
curl http://localhost:8080/cache?action=stats
# → {"size":0,"capacity":1024}
```

---

## How to Run

```bash
# From the project root
./gradlew :cache-manager:run
```

The program:
1. First runs the **Redis-like client demo** showing LRU eviction in action.
2. Then starts the **REST server** on port 8080 for interactive testing.

---

## LRU Eviction Walkthrough

Given a cache with **capacity = 3**:

```
Step 1: put("A","1"), put("B","2"), put("C","3")
        Cache: [A, B, C]    (A = LRU, C = MRU)

Step 2: get("A")             → moves A to MRU
        Cache: [B, C, A]    (B = LRU, A = MRU)

Step 3: put("D","4")         → cache is full, evict LRU (B)
        Cache: [C, A, D]    (C = LRU, D = MRU)
        B is evicted!

Step 4: put("E","5")         → evict LRU (C)
        Cache: [A, D, E]    (A = LRU, E = MRU)
        C is evicted!
```

---

## Design Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `EvictionPolicy` interface + `LRUEvictionPolicy` | Decouples eviction logic from cache; easy to add LFU, FIFO, TTL |
| **Singleton** | `CacheManager` | Single point of access for all cache instances |
| **Adapter** | `RedisLikeClient` / `InMemoryRedisClient` | Maps Redis semantics onto the local cache; swap-friendly |

---

## Concurrency Model

```
Thread 1 (read):  ──[acquire read lock]──[get("A")]──[release]──
Thread 2 (read):  ──[acquire read lock]──[get("B")]──[release]──   ← concurrent
Thread 3 (write): ────────────────────[wait]──[acquire write lock]──[put("C")]──[release]──
```

- Multiple readers never block each other.
- A writer waits for all readers to finish, then holds exclusive access.
- This maximizes throughput for the typical cache read-heavy workload.

---

## Requirement Mapping

Every ask from the problem statement is mapped to a concrete class:

| Requirement | Implementation |
|---|---|
| RESTful APIs to persist and fetch data | `CacheServer` + `CacheHandler` — PUT/GET/DELETE endpoints on port 8080 |
| LRU eviction when cache is full | `LRUEvictionPolicy` using DoublyLinkedList + HashMap — O(1) eviction |
| In-memory data structures | `InMemoryCache` backed by `HashMap` |
| High availability + data consistency | `ReentrantReadWriteLock` — concurrent reads, exclusive writes |
| `redis.get(key)` / `redis.put(key, value)` | `RedisLikeClient` interface + `InMemoryRedisClient` implementation |

### What does the `redis.get` / `redis.put` ask mean?

The interviewer wants to see **two layers**:

- **Layer 1 (programmatic API):** A client abstraction that works like Redis — `redis.get(key)`, `redis.put(key, value)`. This is the `RedisLikeClient` interface. It shows you understand how a cache client abstraction works, and how swapping backends (in-memory vs real Redis) stays transparent to callers.
- **Layer 2 (REST wrapper):** HTTP endpoints that call into the same core cache. This is the `CacheServer` / `CacheHandler`. Both layers share the same underlying `InMemoryCache` through `CacheManager`.

---

## Interview Strategy — How to Approach This in a Timed Round

### Machine Coding Round (1.5–2 hrs, write working code)

This solution is at the right level. Plain Java, no Spring Boot, clean OOP, design patterns, and a working `main()` that demonstrates everything. Interviewers care about:

- O(1) data structure choice (HashMap + DoublyLinkedList)
- Clean separation via interfaces (Strategy pattern for eviction)
- Thread safety (ReadWriteLock, not just `synchronized`)
- Code that actually compiles and runs

### LLD Round (design-focused, less code)

Spend more time on class diagrams and discussing trade-offs (e.g., "why ReadWriteLock over synchronized?", "how would you add TTL?", "how does this scale across nodes?") and write less boilerplate. The REST layer might just be mentioned, not fully coded.

### Recommended Time Split

| Time | What to build | Priority |
|---|---|---|
| **First 30 min** | `InMemoryCache` + `LRUEvictionPolicy` + `DoublyLinkedList` — the core LRU cache with O(1) operations | **Must have** — this is what the interviewer evaluates most |
| **Next 20 min** | `CacheManager` (singleton), thread safety with `ReentrantReadWriteLock`, `RedisLikeClient` interface | **Must have** — shows design maturity |
| **Next 15 min** | REST layer (`CacheServer` + `CacheHandler`) if time permits | **Nice to have** |
| **Last 10 min** | Demo via `Main.java`, discuss extensions (LFU, TTL, distributed cache) | **Nice to have** — shows breadth |

### What to say if short on time

> "I'd skip the REST server and demonstrate through Main.java with the RedisLikeClient. The REST layer is a thin wrapper — the core design (LRU cache, strategy pattern, thread safety) is what matters."

### Common Follow-up Questions Interviewers Ask

| Question | How to answer |
|---|---|
| Why HashMap + DoublyLinkedList instead of LinkedHashMap? | LinkedHashMap works but hides the implementation. Building it yourself shows you understand the internals. Also, LinkedHashMap's `removeEldestEntry` uses `synchronized` internally, giving less control over locking. |
| Why ReadWriteLock over synchronized? | Caches are read-heavy. `synchronized` blocks all threads on every operation. ReadWriteLock allows N concurrent readers and only blocks on writes — much better throughput. |
| How would you add TTL (time-to-live)? | Add an `expiryTimestamp` field to cache entries. On `get()`, check if expired — if yes, evict lazily. Optionally run a background reaper thread for proactive cleanup. |
| How would you make this distributed? | Use consistent hashing to shard keys across nodes. Each node runs this same LRU cache. For cross-node consistency, use a gossip protocol or a coordination service like ZooKeeper. |
| Why not use ConcurrentHashMap directly? | ConcurrentHashMap alone doesn't give LRU ordering. You need the linked list for access-order tracking. Also, compound operations (check-size + evict + insert) need atomicity that ConcurrentHashMap doesn't provide. |

---

## Extending the Solution

- **LFU Eviction**: Implement `EvictionPolicy<K>` with a frequency map + min-heap.
- **TTL Support**: Add expiry timestamps to cache entries; evict on access if expired.
- **Real Redis Backend**: Implement `RedisLikeClient` using Jedis/Lettuce for actual Redis connectivity — callers remain unchanged.
- **Metrics**: Add hit/miss counters to `InMemoryCache` for monitoring.
