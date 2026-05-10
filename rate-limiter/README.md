# Rate Limiter — LLD Interview Practice

## Problem Statement

Design and implement a rate limiter that:

1. Supports multiple rate limiting algorithms (Token Bucket, Fixed Window, Sliding Window Log, Sliding Window Counter).
2. Allows per-client and per-resource rate limiting.
3. Is thread-safe for concurrent access.
4. Returns standard HTTP 429 responses with rate limit headers.

---

## How to Run

```bash
# From the project root
./gradlew :rate-limiter:run
```

**Part 1** runs all 4 algorithms side by side (5 req/2s) showing ALLOWED/REJECTED.
**Part 2** starts a REST server on port 8090 with rate-limited endpoints.

```bash
# Hit the search endpoint (Token Bucket, 10 req/min)
curl 'http://localhost:8090/api/search?clientId=user1'

# Hit the login endpoint (Sliding Window Log, 3 req/min — strict)
curl 'http://localhost:8090/api/login?clientId=user1'
# Hit it 4 times to see 429 Too Many Requests
```

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                    Main.java                     │
│        (demos all algorithms + REST server)      │
└─────────┬─────────────────────────┬──────────────┘
          │                         │
          ▼                         ▼
┌──────────────────┐     ┌────────────────────────┐
│  RateLimiter-     │     │  REST API Layer         │
│  Manager          │     │  (RateLimiterServer +   │
│  (Singleton)      │     │   RateLimiterHandler)   │
│                   │     │                          │
│  Per-resource     │     │  GET /api/{resource}     │
│  rate limiters    │     │  → 200 or 429            │
└───────┬───────────┘     └──────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────┐
│  RateLimiterFactory                        │
│  creates the right algorithm per config    │
└────────────────────┬───────────────────────┘
                     │
    ┌────────────────┼────────────────┬────────────────┐
    ▼                ▼                ▼                ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────────┐
│  Token   │  │  Fixed   │  │  Sliding     │  │  Sliding     │
│  Bucket  │  │  Window  │  │  Window Log  │  │  Window      │
│          │  │  Counter │  │              │  │  Counter     │
└──────────┘  └──────────┘  └──────────────┘  └──────────────┘

All implement RateLimiter interface (Strategy Pattern)
```

---

## Algorithm Deep Dive

### 1. Token Bucket

```
Bucket capacity = 5 tokens, refill rate = 5 tokens per window

Time 0:   [●●●●●] 5 tokens — bucket full
Request:  [●●●●○] 4 tokens — consumed 1
Request:  [●●●○○] 3 tokens
Request:  [●●○○○] 2 tokens
Request:  [●○○○○] 1 token
Request:  [○○○○○] 0 tokens
Request:  REJECTED — bucket empty
  ...time passes, tokens refill gradually...
Request:  [●○○○○] 1 token refilled — ALLOWED
```

**Key properties:**
- Allows **bursts** up to the bucket capacity.
- Smooth refill — doesn't wait for window boundary.
- Most popular: AWS API Gateway, Stripe, Google Cloud.

**Implementation:** `TokenBucket` inner class with `currentTokens` and `lastRefillTime`. On each request: refill based on elapsed time, then try to consume 1 token.

### 2. Fixed Window Counter

```
Window 1 (0:00 - 1:00)         Window 2 (1:00 - 2:00)
┌─────────────────────┐       ┌─────────────────────┐
│ count: 0→1→2→3→4→5  │       │ count: 0→1→...      │
│ limit: 5             │       │ reset!               │
│ req 6: REJECTED      │       │                      │
└─────────────────────┘       └─────────────────────┘

⚠ BOUNDARY BURST PROBLEM:
  At 0:59 → 5 requests (end of window 1)
  At 1:01 → 5 requests (start of window 2)
  = 10 requests in 2 seconds, but limit is 5/minute!
```

**Key properties:**
- Simplest to implement.
- **Boundary burst problem** — 2x burst at window edges.
- Good when approximate limiting is acceptable.

### 3. Sliding Window Log

```
Window = last 60 seconds, limit = 5

Timestamp log (sorted queue):
  [10:00:05, 10:00:20, 10:00:35, 10:00:50, 10:01:05]
                                                  ↑ 5 entries in window

New request at 10:01:10:
  1. Evict timestamps before 10:00:10 → remove 10:00:05
     [10:00:20, 10:00:35, 10:00:50, 10:01:05]  → 4 entries
  2. 4 < 5 → ALLOWED, add 10:01:10
     [10:00:20, 10:00:35, 10:00:50, 10:01:05, 10:01:10]
```

**Key properties:**
- **Most precise** — no boundary problem.
- **Highest memory** — stores every timestamp.
- Best for security-sensitive endpoints (login, OTP).

### 4. Sliding Window Counter (Hybrid)

```
Previous window (0:00-1:00): 4 requests
Current  window (1:00-2:00): 2 requests
Current time: 1:20 (20s into current window, i.e., 33%)

Weighted estimate:
  previousWeight = (60 - 20) / 60 = 0.667  (67% of prev window overlaps)
  estimated = 4 × 0.667 + 2 = 4.67 ≈ 4
  limit = 5 → remaining = 1 → ALLOWED

This smooths the fixed window boundary problem with minimal memory.
```

**Key properties:**
- **Best balance** of precision and memory (only 2 counters per client).
- Used by Cloudflare, Redis-based rate limiters.
- Eliminates ~99% of boundary burst cases.

---

## Algorithm Comparison

| Algorithm | Time | Space (per client) | Burst-friendly | Precision | Best for |
|---|---|---|---|---|---|
| **Token Bucket** | O(1) | O(1) — 2 fields | Yes (allows burst) | High | API gateways, general use |
| **Fixed Window** | O(1) | O(1) — 1 counter | No (boundary burst) | Low | Simple use cases |
| **Sliding Window Log** | O(n) amortized | O(maxReq) — stores timestamps | No | Highest | Login, security endpoints |
| **Sliding Window Counter** | O(1) | O(1) — 2 counters | Smooth | High | High-volume APIs |

---

## Class-by-Class Breakdown

| Class | Responsibility |
|---|---|
| `RateLimiter` | Strategy interface — `tryAcquire(clientId)` returns `RateLimitResult` |
| `RateLimiterConfig` | Immutable config — `maxRequests` + `window` duration |
| `RateLimitResult` | Result record — `allowed`, `limit`, `remaining`, `retryAfterMillis` |
| `RateLimiterFactory` | Factory — creates the right algorithm from an enum |
| `RateLimiterManager` | Singleton — maps resource names to rate limiters |
| `TokenBucketRateLimiter` | Token bucket with gradual refill |
| `FixedWindowRateLimiter` | Fixed window counter with reset on rollover |
| `SlidingWindowLogRateLimiter` | Timestamp log with eviction |
| `SlidingWindowCounterRateLimiter` | Weighted hybrid of current + previous window |
| `RateLimiterHandler` | HTTP handler — returns 200 or 429 with rate limit headers |
| `RateLimiterServer` | JDK HttpServer with thread pool |

---

## Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `RateLimiter` interface + 4 algorithm implementations | Swap algorithms without changing caller code |
| **Factory** | `RateLimiterFactory.create(algorithm, config)` | Centralized creation, decouples client from concrete classes |
| **Singleton** | `RateLimiterManager.getInstance()` | Single registry of all rate limiters |
| **Record** | `RateLimitResult`, `RateLimiterConfig` | Immutable value objects (Java 17 records) |

---

## Thread Safety

Each algorithm uses `synchronized` on its per-client inner object:

```
Client "user-1" → TokenBucket instance → synchronized tryConsume()
Client "user-2" → TokenBucket instance → synchronized tryConsume()
                                          ↑ different locks, no contention
```

- **Per-client locking**: requests from different clients never block each other.
- **ConcurrentHashMap**: the client → bucket mapping is thread-safe.
- `computeIfAbsent`: atomic bucket creation for new clients.

---

## HTTP Rate Limit Headers (Industry Standard)

```
HTTP/1.1 200 OK                      HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 10                X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7             X-RateLimit-Remaining: 0
                                     Retry-After: 45
```

These headers are used by GitHub API, Twitter API, Stripe, and most modern REST APIs.

---

## Interview Time Strategy

| Time | What to build | Priority |
|---|---|---|
| **First 15 min** | `RateLimiter` interface + `TokenBucketRateLimiter` | **Must have** — token bucket is the most asked |
| **Next 15 min** | `RateLimiterConfig`, `RateLimitResult`, per-client tracking | **Must have** |
| **Next 10 min** | `SlidingWindowCounterRateLimiter` (mention all 4 algorithms, implement 2) | **Should have** |
| **Next 10 min** | `RateLimiterManager` + `RateLimiterFactory` | **Nice to have** |
| **Last 10 min** | REST endpoint or Main demo + discuss trade-offs | **Nice to have** |

**If short on time:** Implement Token Bucket only, mention the other three with trade-offs. The interviewer cares more about your understanding of the trade-offs than implementing all four.
