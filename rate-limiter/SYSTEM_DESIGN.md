# Rate Limiter — System Design (Interview Reference)

## 1. Problem Definition

> "Design a rate limiter that can handle millions of users and thousands of requests per second across a distributed system."

### Functional Requirements

- Limit the number of requests a client can make within a time window.
- Support different rate limits per API endpoint (e.g., `/login` = 5/min, `/search` = 100/min).
- Return HTTP 429 (Too Many Requests) with `Retry-After` header when limit is exceeded.
- Support rate limiting by: user ID, IP address, API key.

### Non-Functional Requirements

- **Low latency**: rate check must add < 1ms to request path.
- **High availability**: rate limiter failure should NOT block requests (fail-open).
- **Distributed**: work correctly across multiple API servers.
- **Accurate**: no single user should exceed the defined limit globally.
- **Scalable**: handle millions of users, thousands of QPS.

---

## 2. High-Level Architecture

```
                        ┌──────────────────┐
                        │    Client App     │
                        └────────┬─────────┘
                                 │
                                 ▼
                        ┌──────────────────┐
                        │   Load Balancer   │
                        │   (Nginx / ALB)   │
                        └────────┬─────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                   ▼
    ┌──────────────┐   ┌──────────────┐    ┌──────────────┐
    │  API Server  │   │  API Server  │    │  API Server  │
    │  Instance 1  │   │  Instance 2  │    │  Instance 3  │
    └──────┬───────┘   └──────┬───────┘    └──────┬───────┘
           │                  │                    │
           │     ┌────────────┴────────────┐       │
           └─────►      Rate Limiter       ◄───────┘
                 │      Middleware          │
                 └────────────┬────────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │     Redis Cluster      │
                 │  (Centralized State)   │
                 │                        │
                 │  Key: rate:{userId}    │
                 │  Value: counter / log  │
                 │  TTL: window duration  │
                 └────────────────────────┘
```

### Why Redis?

| Concern | Why Redis solves it |
|---|---|
| Shared state | All API servers read/write the same counters |
| Speed | In-memory, ~0.1ms latency — minimal overhead |
| Atomicity | `INCR`, `EXPIRE` are atomic; Lua scripts for compound operations |
| TTL | Auto-expire counters when window rolls over |
| Cluster mode | Horizontal scaling for high throughput |

---

## 3. Where to Place the Rate Limiter?

```
Option A: Client-side          Option B: Server-side         Option C: Middleware
┌────────┐                     ┌────────┐                    ┌────────┐
│ Client │─── rate check ──►   │ Client │──►┌─────────┐     │ Client │──►┌──────────┐──►┌─────────┐
│        │                     │        │   │  API    │     │        │   │ API      │   │  API    │
└────────┘                     └────────┘   │  Server │     └────────┘   │ Gateway  │   │  Server │
                                            │ (check) │                  │ (check)  │   │         │
  ✗ Can be bypassed            └─────────┘  │         │                  └──────────┘   └─────────┘
  ✗ No control                              └─────────┘
                                ✓ Full control                ✓ Centralized
                                ✗ Each server needs code      ✓ Single point
                                                              ✓ Language-agnostic
```

**Best practice: Option C (API Gateway / Middleware)**. This is what AWS API Gateway, Kong, Nginx, and Envoy do. The rate limiter is a cross-cutting concern — it shouldn't live in business logic.

---

## 4. Distributed Rate Limiting — The Hard Problem

### Problem: Multiple Servers, One User

```
User "alice" sends 10 requests.
Load balancer spreads them across 3 servers.

Server 1: sees 3 requests → allows (limit=10)
Server 2: sees 4 requests → allows (limit=10)
Server 3: sees 3 requests → allows (limit=10)

Total: 10 requests allowed ✓ (in this case)

But what if limit = 5?
Server 1: sees 3 → allows (< 5)
Server 2: sees 4 → allows (< 5)
Server 3: sees 3 → allows (< 5)
Total: 10 allowed, but limit is 5! ✗ BROKEN
```

### Solution A: Centralized Store (Redis)

```
All servers check Redis before processing:

  Server 1 ─┐
  Server 2 ──┼──► Redis INCR rate:alice → returns 1, 2, 3...
  Server 3 ─┘

  When counter > limit → reject
```

**Pros:** Globally accurate, simple.
**Cons:** Redis becomes a dependency — if it goes down, rate limiting breaks.

### Solution B: Sticky Sessions

```
Load Balancer routes all of "alice"'s requests to the same server.
Each server rate-limits locally (no Redis needed).

  alice → always Server 2 (via IP hash / cookie)
```

**Pros:** No external dependency, very fast.
**Cons:** Uneven load distribution, not resilient to server restarts.

### Solution C: Local Rate Limiter + Sync (Gossip)

```
Each server maintains a local counter.
Periodically syncs counts with other servers via gossip protocol.

  Server 1: local=3 ──sync──► Server 2: local=4
  Server 2 now knows: global ≈ 3+4+3 = 10

  Eventual consistency — may briefly exceed limit.
```

**Pros:** No single point of failure, fast.
**Cons:** Approximate, briefly allows over-limit.

### Recommendation

| Scenario | Approach |
|---|---|
| Most common (SDE interviews) | **Redis-based centralized** — simple, accurate, easy to explain |
| Ultra-low latency required | Local + gossip sync |
| Simple setup, moderate traffic | Sticky sessions |

---

## 5. Redis Implementation Details

### Token Bucket in Redis (Lua Script)

```lua
-- KEYS[1] = rate limit key (e.g., "rate:user123:/api/search")
-- ARGV[1] = max tokens (bucket capacity)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = current timestamp (seconds)

local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or max_tokens
local last_refill = tonumber(bucket[2]) or now

-- Refill tokens based on time elapsed
local elapsed = now - last_refill
local new_tokens = math.min(max_tokens, tokens + elapsed * refill_rate)

if new_tokens >= 1 then
    new_tokens = new_tokens - 1
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    redis.call('EXPIRE', key, max_tokens / refill_rate * 2)
    return {1, new_tokens}  -- allowed, remaining
else
    redis.call('HMSET', key, 'tokens', new_tokens, 'last_refill', now)
    return {0, 0}  -- rejected
end
```

**Why Lua?** The entire read-modify-write is executed atomically in Redis. No race conditions, no distributed locks needed.

### Fixed Window Counter in Redis

```
SET key = "rate:{userId}:{windowId}"
INCR key
if count == 1:
    EXPIRE key {windowSeconds}
if count > limit:
    return REJECTED
```

### Sliding Window Log in Redis

```
ZADD rate:{userId} {timestamp} {requestId}
ZREMRANGEBYSCORE rate:{userId} 0 {now - windowMs}
ZCARD rate:{userId}
if count > limit:
    return REJECTED
```

---

## 6. Race Conditions

### The Check-Then-Act Problem

```
Thread 1: READ counter → 4
Thread 2: READ counter → 4        ← both see 4
Thread 1: counter < 5? YES → INCR → 5
Thread 2: counter < 5? YES → INCR → 6  ← EXCEEDED LIMIT!
```

### Solutions

| Solution | How | Trade-off |
|---|---|---|
| **Redis INCR** | Atomic increment, check after | Simple, handles 99% of cases |
| **Lua Script** | Entire check+increment is atomic | Most correct, slightly more complex |
| **Distributed Lock** | Redis SETNX / Redlock | Overkill for rate limiting, adds latency |

**Recommendation:** Use Redis `INCR` for fixed window, Lua scripts for token bucket/sliding window.

---

## 7. Failure Modes — What If Redis Goes Down?

```
┌────────────────────────────────────────────────────────────┐
│                      FAIL-OPEN                             │
│                                                            │
│  If Redis is unreachable → ALLOW all requests              │
│                                                            │
│  Rationale: It's better to serve all users (including       │
│  potential abusers) than to block all users.                │
│                                                            │
│  Mitigation: Local in-memory fallback rate limiter          │
│  kicks in until Redis recovers.                            │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│                     FAIL-CLOSED                            │
│                                                            │
│  If Redis is unreachable → REJECT all requests             │
│                                                            │
│  Rationale: Security-first (e.g., prevent brute-force       │
│  attacks even if Redis is down).                           │
│                                                            │
│  Risk: Total service outage if Redis is down.              │
└────────────────────────────────────────────────────────────┘
```

**Most APIs use fail-open.** Critical security endpoints (login, payment) may use fail-closed.

---

## 8. Rate Limiting Rules — Where to Store Them?

```yaml
# Configuration stored in a database or config service
rules:
  - resource: "/api/search"
    algorithm: TOKEN_BUCKET
    limit: 100
    window: 60s
    scope: PER_USER

  - resource: "/api/login"
    algorithm: SLIDING_WINDOW_LOG
    limit: 5
    window: 300s
    scope: PER_IP

  - resource: "/api/upload"
    algorithm: TOKEN_BUCKET
    limit: 10
    window: 3600s
    scope: PER_API_KEY
```

Rules should be **hot-reloadable** (fetch from DB/config service periodically, not hardcoded). This lets ops teams adjust limits without redeployment.

---

## 9. Full Request Flow

```
1. Client sends request
         │
         ▼
2. API Gateway extracts client ID (user ID / IP / API key)
         │
         ▼
3. Rate Limiter checks Redis:
     ┌─── ALLOWED ───────────────────────────────────┐
     │    - Forward request to backend service        │
     │    - Set X-RateLimit-Remaining header           │
     │    - Return 200 with response                   │
     └────────────────────────────────────────────────┘
     ┌─── REJECTED ──────────────────────────────────┐
     │    - Return 429 Too Many Requests              │
     │    - Set Retry-After header                     │
     │    - Log the rate limit event                   │
     └────────────────────────────────────────────────┘
     ┌─── REDIS DOWN ───────────────────────────────┐
     │    - FAIL-OPEN: allow request                  │
     │    - Use local in-memory fallback              │
     │    - Alert ops team                             │
     └────────────────────────────────────────────────┘
```

---

## 10. Capacity Estimation

### Example: 1 million active users, 500 req/sec

**Redis memory per user:**

| Algorithm | Storage per user | Total for 1M users |
|---|---|---|
| Token Bucket | ~50 bytes (2 fields) | ~50 MB |
| Fixed Window | ~30 bytes (1 counter + TTL) | ~30 MB |
| Sliding Window Log | ~50 bytes × maxReq (timestamps) | ~500 MB (if 100 req/user) |
| Sliding Window Counter | ~60 bytes (2 counters) | ~60 MB |

**Redis throughput:** Single Redis node handles 100K+ ops/sec. For 500 req/sec, a single node is sufficient. For higher loads, use Redis Cluster.

---

## 11. Trade-offs Summary

| Decision | Option A | Option B | Recommendation |
|---|---|---|---|
| **Algorithm** | Token Bucket (burst-friendly) | Sliding Window Counter (smooth) | Token Bucket for APIs, Sliding Window for security |
| **State store** | Redis (centralized, accurate) | Local memory (fast, approximate) | Redis for distributed, local for single-server |
| **Failure mode** | Fail-open (allow all) | Fail-closed (reject all) | Fail-open for most, fail-closed for auth endpoints |
| **Scope** | Per-user | Per-IP | Per-user for authenticated APIs, per-IP for public |
| **Placement** | API Gateway (centralized) | Each service (decentralized) | API Gateway for consistency |
| **Precision** | Exact (Lua scripts) | Approximate (local + sync) | Exact for security, approximate for throughput |

---

## 12. Interview Questions & Answers

### Q1: Why not just use `synchronized` or in-memory rate limiting?

**A:** In a single-server setup, in-memory works. But in production with multiple API server instances behind a load balancer, each server only sees a fraction of the user's requests. A user making 100 requests gets ~33 per server — each server thinks the user is within limits. You need a **centralized store** (Redis) so all servers share the same counters.

### Q2: Which algorithm would you pick and why?

**A:** For a general-purpose API, **Token Bucket**. It allows controlled bursts (good for UX — a user refreshing a page rapidly shouldn't be blocked immediately), has O(1) time and space, and is what AWS, Stripe, and Google use. For login/OTP endpoints, **Sliding Window Log** because precision matters more than performance there.

### Q3: How does the rate limiter handle different rate limits for different APIs?

**A:** The rate limiter key includes both the client ID and the resource:
```
Key: rate:{userId}:/api/search   → 100 req/min (Token Bucket)
Key: rate:{userId}:/api/login    → 5 req/5min  (Sliding Window Log)
```
Each resource has its own configuration stored in a rules database, hot-reloaded by the rate limiter service.

### Q4: What happens if Redis goes down?

**A:** **Fail-open**: allow all requests and fall back to a local in-memory rate limiter per server. This is approximate but prevents total service outage. Alert the ops team immediately. For security-critical endpoints (login), consider **fail-closed** to prevent brute-force attacks.

### Q5: How do you prevent race conditions in Redis?

**A:** Use **Lua scripts**. A Lua script runs atomically in Redis — the entire read-check-update sequence executes as a single operation. No other command can interleave. This is simpler and faster than distributed locks.

### Q6: Fixed Window vs Sliding Window — what's the boundary burst problem?

**A:** Fixed Window resets the counter at exact window boundaries (e.g., every 60s). A user can make N requests at :59 and N more at :01 — 2N requests in 2 seconds despite an N/minute limit. Sliding Window Counter solves this by weighting the previous window's count based on how much of it overlaps with the current sliding window, giving a smooth estimated count.

### Q7: How do you rate limit in a microservices architecture?

**A:** Use an **API Gateway** (Kong, Envoy, AWS API Gateway) as the single entry point. The gateway applies rate limiting before routing to backend services. This is better than each microservice implementing its own rate limiter because:
- Single point of configuration
- Consistent behavior across all services
- Backend services don't need rate limiting code

### Q8: How would you implement rate limiting for a multi-tenant SaaS?

**A:** Three-tier rate limiting:
1. **Global limit**: 10,000 req/min across all tenants (protect infrastructure).
2. **Per-tenant limit**: based on plan (Free=100/min, Pro=1000/min, Enterprise=10000/min).
3. **Per-user within tenant**: prevent one user from consuming the tenant's entire quota.

Key = `rate:{tenantId}:{userId}:{resource}`

### Q9: How do you monitor and alert on rate limiting?

**A:** Track these metrics:
- Rate limit hit rate (% of requests rejected)
- Per-user rejection counts (detect abusers)
- Redis latency (performance health)
- Redis availability (trigger fail-open)
- Top rate-limited endpoints (capacity planning)

Use Prometheus + Grafana or CloudWatch for dashboards and alerts.

### Q10: Token Bucket vs Leaky Bucket — what's the difference?

**A:**
- **Token Bucket**: tokens accumulate over time, requests consume tokens. Allows **bursts** up to bucket capacity.
- **Leaky Bucket**: requests enter a queue (bucket), processed at a fixed rate. Excess requests overflow (rejected). Enforces **constant output rate** — no bursts.

Token Bucket is more common for APIs because users expect bursty access patterns. Leaky Bucket is used in network traffic shaping where constant throughput is needed.

---

## 13. Real-World Implementations

| Company | Algorithm | Details |
|---|---|---|
| **Stripe** | Token Bucket | Per-API-key, published limits, Retry-After headers |
| **GitHub API** | Fixed Window | 5,000 req/hour for authenticated, 60/hour for unauthenticated |
| **Cloudflare** | Sliding Window Counter | Per-IP and per-zone, configurable rules |
| **AWS API Gateway** | Token Bucket | Per-stage, per-method, burst + steady-state limits |
| **Twitter/X** | Fixed Window | 15 min windows, per-endpoint limits |
| **Google Cloud** | Token Bucket | Per-project quotas, refill over time |
