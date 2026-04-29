# Machine Coding — Order & Inventory Management REST APIs

> **Interview-ready** Spring Boot project for 1-hour machine coding rounds.
> Study this before the round. Understand every layer, every pattern, every edge case.

---

## Quick Start

```bash
./gradlew bootRun
# App starts on http://localhost:8080
# Health check: curl http://localhost:8080/health
```

---

## Table of Contents

1. [How to Approach Any Machine Coding Round](#1-how-to-approach-any-machine-coding-round)
2. [Minute-by-Minute Time Management](#2-minute-by-minute-time-management)
3. [Project Structure](#3-project-structure)
4. [API Reference with curl Examples](#4-api-reference-with-curl-examples)
5. [End-to-End Flow Walkthrough](#5-end-to-end-flow-walkthrough)
6. [Design Patterns Used & Why](#6-design-patterns-used--why)
7. [Key Concepts to Explain in Interview](#7-key-concepts-to-explain-in-interview)
8. [Thread Safety — How & Why](#8-thread-safety--how--why)
9. [Edge Cases Handled](#9-edge-cases-handled)
10. [Order State Machine](#10-order-state-machine)
11. [Stock Lifecycle](#11-stock-lifecycle)
12. [Common Follow-Up Questions & Answers](#12-common-follow-up-questions--answers)
13. [What to Say in Last 5 Minutes](#13-what-to-say-in-last-5-minutes)
14. [Checklist Before Interview](#14-checklist-before-interview)

---

## 1. How to Approach Any Machine Coding Round

When you get the problem statement, follow this exact sequence:

### Step 1: Read & Identify (2 min)
```
Read problem → Extract NOUNS (these are your entities)
                Extract VERBS (these are your APIs)
                Extract RULES (these are your validations)
```

### Step 2: Define Entities (3 min)
```
For each noun, ask:
  - Does it have its own identity? → Model class
  - Is it a fixed set of values? → Enum
  - Is it data flowing in/out of API? → DTO (Java Record)
```

### Step 3: Define APIs (2 min)
```
Map verbs to HTTP methods:
  Create → POST
  Read   → GET
  Update → PUT
  Delete → DELETE
  Action → PUT /resource/{id}/action (e.g., /orders/{id}/confirm)
```

### Step 4: Code in This Order
```
1. Enums           (30 seconds each)
2. Models          (1-2 min each)
3. Request DTOs    (30 seconds each, use Java Records)
4. Service layer   (5-8 min each, this is the CORE)
5. Controller      (2-3 min each, thin layer)
6. Test with curl  (as you go)
```

---

## 2. Minute-by-Minute Time Management

| Time | What to Do | Don't Do |
|------|-----------|----------|
| **0-5 min** | Read problem. Identify entities, enums, APIs. Write them down. | Don't start coding yet |
| **5-8 min** | Create enums + model classes with Lombok | Don't add business logic to models |
| **8-12 min** | Create request DTOs (Java Records with validation) | Don't create unnecessary DTOs |
| **12-35 min** | Implement services one by one. Test each with curl. | Don't write all services then test — test incrementally |
| **35-50 min** | Implement controllers. Wire to services. Test full flow. | Don't over-engineer controllers — keep them thin |
| **50-55 min** | Add edge case handling, validations, error responses | Don't add new features |
| **55-60 min** | Quick demo. Walk through code structure. Mention extensions. | Don't panic-code |

### Golden Rules
- **ConcurrentHashMap** for storage, never HashMap
- **AtomicLong** for ID generation
- **Java Records** for DTOs (shows Java 17 knowledge)
- **@Valid** on request bodies
- **Proper HTTP status codes**: 201 Created, 400 Bad Request, 404 Not Found, 409 Conflict
- **Test as you build** — don't wait until the end

---

## 3. Project Structure

```
com.design.machinecoding/
├── MachineCodingApplication.java       ← @SpringBootApplication entry point
│
├── controller/                         ← Thin REST layer (no business logic here)
│   ├── HealthController.java           GET  /health
│   ├── ProductController.java          CRUD /api/products
│   ├── WarehouseController.java        CRUD /api/warehouses
│   ├── InventoryController.java        /api/inventory (stock ops)
│   └── OrderController.java           /api/orders (lifecycle)
│
├── service/                            ← Business logic lives HERE
│   ├── ProductService.java             Product CRUD with in-memory store
│   ├── WarehouseService.java           Warehouse CRUD
│   ├── InventoryService.java           Stock add/reserve/confirm/release + low stock alert
│   └── OrderService.java              Order placement with saga rollback + lifecycle
│
├── model/                              ← Domain entities (Lombok)
│   ├── Product.java                    @Data @Builder — id, name, category, price
│   ├── Warehouse.java                  @Data @Builder — id, name, location
│   ├── Inventory.java                  Synchronized stock tracking (the CORE class)
│   ├── Order.java                      @Data @Builder — id, customer, items, status, total
│   └── OrderItem.java                  @Data @Builder — productId, qty, unitPrice, warehouseId
│
├── dto/                                ← Request/Response DTOs (Java Records)
│   ├── ApiResponse.java                Generic wrapper: {success, message, data, timestamp}
│   ├── CreateProductRequest.java       @NotBlank name, @NotNull category, @Positive price
│   ├── UpdateProductRequest.java       Optional fields for partial update
│   ├── CreateWarehouseRequest.java     @NotBlank name, location
│   ├── AddStockRequest.java            warehouseId, productId, quantity
│   ├── PlaceOrderRequest.java          customerName, List<OrderItemRequest>
│   ├── OrderItemRequest.java           productId, quantity
│   └── InventoryResponse.java          Enriched response with product/warehouse names
│
├── enums/
│   ├── Category.java                   ELECTRONICS, CLOTHING, GROCERY, FURNITURE, SPORTS
│   └── OrderStatus.java                PENDING → CONFIRMED → SHIPPED → DELIVERED / CANCELLED
│
└── exception/
    ├── GlobalExceptionHandler.java     @RestControllerAdvice — catches everything
    ├── ResourceNotFoundException.java  → 404
    ├── BadRequestException.java        → 400
    └── ConflictException.java          → 409
```

**Why this structure matters**: Interviewer sees clean separation of concerns at a glance.
Controllers are thin (just delegate). Services have all logic. Models are pure data.

---

## 4. API Reference with curl Examples

### 4a. Product APIs

```bash
# CREATE product
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15","category":"ELECTRONICS","price":999.99,"description":"Latest iPhone"}'

# GET all products
curl http://localhost:8080/api/products

# GET product by ID
curl http://localhost:8080/api/products/1

# GET products by category
curl http://localhost:8080/api/products/category/ELECTRONICS

# UPDATE product
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"price":899.99,"description":"Updated description"}'

# DELETE product
curl -X DELETE http://localhost:8080/api/products/1
```

### 4b. Warehouse APIs

```bash
# CREATE warehouse
curl -X POST http://localhost:8080/api/warehouses \
  -H "Content-Type: application/json" \
  -d '{"name":"Mumbai Hub","location":"Mumbai, India"}'

# GET all warehouses
curl http://localhost:8080/api/warehouses

# GET warehouse by ID
curl http://localhost:8080/api/warehouses/1
```

### 4c. Inventory APIs

```bash
# ADD stock to warehouse
curl -X POST http://localhost:8080/api/inventory/add-stock \
  -H "Content-Type: application/json" \
  -d '{"warehouseId":1,"productId":1,"quantity":50}'

# GET all inventory in a warehouse
curl http://localhost:8080/api/inventory/warehouse/1

# GET stock for a product across all warehouses
curl http://localhost:8080/api/inventory/product/1

# CHECK if quantity is available
curl "http://localhost:8080/api/inventory/check?productId=1&quantity=10"
```

### 4d. Order APIs

```bash
# PLACE order (reserves stock)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Puja","items":[{"productId":1,"quantity":2},{"productId":2,"quantity":3}]}'

# GET all orders
curl http://localhost:8080/api/orders

# GET order by ID
curl http://localhost:8080/api/orders/1

# CONFIRM order (permanently deducts stock)
curl -X PUT http://localhost:8080/api/orders/1/confirm

# CANCEL order (releases reserved stock)
curl -X PUT http://localhost:8080/api/orders/1/cancel

# SHIP order
curl -X PUT http://localhost:8080/api/orders/1/ship

# DELIVER order
curl -X PUT http://localhost:8080/api/orders/1/deliver
```

---

## 5. End-to-End Flow Walkthrough

This is the **demo flow** to run during the interview to show everything works:

```bash
# Step 1: Create products
curl -s -X POST localhost:8080/api/products -H "Content-Type: application/json" \
  -d '{"name":"Laptop","category":"ELECTRONICS","price":1200,"description":"MacBook Pro"}'
curl -s -X POST localhost:8080/api/products -H "Content-Type: application/json" \
  -d '{"name":"Headphones","category":"ELECTRONICS","price":250,"description":"Sony WH-1000XM5"}'

# Step 2: Create warehouse
curl -s -X POST localhost:8080/api/warehouses -H "Content-Type: application/json" \
  -d '{"name":"Delhi Warehouse","location":"Delhi"}'

# Step 3: Add stock
curl -s -X POST localhost:8080/api/inventory/add-stock -H "Content-Type: application/json" \
  -d '{"warehouseId":1,"productId":1,"quantity":20}'
curl -s -X POST localhost:8080/api/inventory/add-stock -H "Content-Type: application/json" \
  -d '{"warehouseId":1,"productId":2,"quantity":50}'

# Step 4: Check availability
curl -s "localhost:8080/api/inventory/check?productId=1&quantity=5"
# → available: true

# Step 5: Place order (stock gets RESERVED, not deducted yet)
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"customerName":"Puja","items":[{"productId":1,"quantity":3},{"productId":2,"quantity":2}]}'
# → status: PENDING, totalAmount: 4100.0

# Step 6: Check inventory — reserved shows up
curl -s localhost:8080/api/inventory/warehouse/1
# → Laptop: total=20, reserved=3, available=17
# → Headphones: total=50, reserved=2, available=48

# Step 7: Confirm order (stock permanently deducted)
curl -s -X PUT localhost:8080/api/orders/1/confirm
# → status: CONFIRMED

# Step 8: Check inventory — total reduced
curl -s localhost:8080/api/inventory/warehouse/1
# → Laptop: total=17, reserved=0, available=17

# Step 9: Ship → Deliver
curl -s -X PUT localhost:8080/api/orders/1/ship
curl -s -X PUT localhost:8080/api/orders/1/deliver
# → status: DELIVERED

# --- Failure Scenario: Insufficient stock ---
curl -s -X POST localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"customerName":"Test","items":[{"productId":1,"quantity":9999}]}'
# → 400: "Insufficient stock for product: Laptop (id=1)"

# --- Failure Scenario: Invalid state transition ---
curl -s -X PUT localhost:8080/api/orders/1/cancel
# → 400: "Only PENDING orders can be cancelled. Current: DELIVERED"
```

---

## 6. Design Patterns Used & Why

### 6a. Builder Pattern (Lombok @Builder)
**Where:** All models — Product, Warehouse, Order, OrderItem
**Why:** Clean object creation without telescoping constructors.
```java
Order.builder()
    .id(1L)
    .customerName("Puja")
    .items(items)
    .status(OrderStatus.PENDING)
    .createdAt(LocalDateTime.now())
    .build();
```
**Interview line:** "Builder gives me immutable-like construction with readability. Java Records don't work here because Order's status is mutable."

### 6b. Saga Pattern (Compensating Transactions)
**Where:** `OrderService.placeOrder()`
**Why:** Multi-item orders span multiple inventory records. If item 3 fails, items 1 and 2 must be rolled back.

```java
try {
    for (item : request.items()) {
        reserve(item);        // reserve stock
        reservedItems.add(item);
    }
} catch (Exception e) {
    for (item : reservedItems) {
        release(item);        // COMPENSATE — undo all reservations
    }
    throw e;
}
```

**Interview line:** "This is a saga with compensating actions. Each step is independently atomic (synchronized inventory). If any step fails, all previous steps are undone. In a microservices world, I'd use an event-driven saga with a saga orchestrator."

### 6c. State Machine (Order Lifecycle)
**Where:** `OrderService` — confirm/cancel/ship/deliver methods
**Why:** Orders have strict transitions. You can't ship a PENDING order or cancel a DELIVERED one.

```
PENDING → CONFIRMED → SHIPPED → DELIVERED
   ↓
CANCELLED
```

**Interview line:** "The state machine prevents invalid transitions at the service layer. Each transition method validates the current state before proceeding. In production, I'd use a state machine library like Spring StateMachine for complex flows."

### 6d. Repository Pattern (In-Memory)
**Where:** Every service uses `ConcurrentHashMap<Long, Entity>` + `AtomicLong` for ID generation
**Why:** No database needed. Same interface as a real repository.

**Interview line:** "I'm using in-memory maps as a repository layer. Swapping to JPA would only change the storage — service logic remains identical. ConcurrentHashMap gives me thread-safe reads/writes."

### 6e. DTO Pattern (Java Records)
**Where:** All request/response DTOs
**Why:** Separates API contract from internal models.

```java
public record CreateProductRequest(
    @NotBlank String name,
    @NotNull Category category,
    @Positive double price,
    String description
) {}
```

**Interview line:** "Java Records are perfect for DTOs — immutable, compact, auto-generate equals/hashCode/toString. Validation annotations on records work out of the box with Spring."

### 6f. Global Exception Handling (@RestControllerAdvice)
**Where:** `GlobalExceptionHandler`
**Why:** Centralized error handling. No try-catch in controllers.

| Exception | HTTP Status | When |
|-----------|-------------|------|
| ResourceNotFoundException | 404 | Entity not found by ID |
| BadRequestException | 400 | Invalid operation (cancel a delivered order) |
| ConflictException | 409 | Duplicate resource |
| MethodArgumentNotValidException | 400 | @Valid fails (blank name, negative price) |
| Exception (catch-all) | 500 | Unexpected errors |

**Interview line:** "I keep controllers clean by handling all exceptions in one place. Each exception maps to a specific HTTP status code. The response format is consistent — always `{success, message, data, timestamp}`."

---

## 7. Key Concepts to Explain in Interview

### 7a. Reserve → Confirm → Release Pattern

This is the **most important concept** in this system:

```
Place Order:    available stock → reserved stock      (customer claims it)
Confirm Order:  reserved stock  → sold                (payment done, stock gone)
Cancel Order:   reserved stock  → available stock     (customer changed mind)
```

**Three numbers tracked in Inventory:**
```
totalQuantity    = physically in warehouse
reservedQuantity = claimed by pending orders
availableQuantity = totalQuantity - reservedQuantity
```

**Confirm** reduces BOTH total and reserved (stock leaves warehouse).
**Cancel** reduces ONLY reserved (stock goes back to available pool).

### 7b. Price Snapshotting

```java
OrderItem.builder()
    .unitPrice(product.getPrice())   // captured at order time
    .build();
```

If admin changes the product price AFTER an order is placed, existing orders are NOT affected.
`OrderItem.unitPrice` is a snapshot, not a reference.

**Interview line:** "I snapshot the price at order creation time. This prevents retroactive price changes from affecting existing orders. It's the same pattern Amazon uses — your order total doesn't change if the price goes up the next day."

### 7c. Why ConcurrentHashMap, Not HashMap

```
HashMap:           NOT thread-safe. Two threads writing simultaneously can corrupt internal structure.
ConcurrentHashMap: Thread-safe reads AND writes. No external synchronization needed.
                   Uses segment-level locking internally — concurrent reads are fast.
```

### 7d. Why AtomicLong for ID Generation

```
long counter++:     NOT atomic. Two threads can get the same ID.
AtomicLong:         Uses CAS (Compare-And-Swap). Guaranteed unique IDs without synchronized.
```

---

## 8. Thread Safety — How & Why

### The Race Condition We Prevent

```
Time  Thread A (Order 1)        Thread B (Order 2)      Available
────  ─────────────────         ─────────────────       ─────────
T1    read available = 5                                    5
T2                               read available = 5        5
T3    5 >= 3? YES                                          5
T4                               5 >= 4? YES               5
T5    reserved += 3                                        2
T6                               reserved += 4            -2 ← OVERSOLD!
```

### How `synchronized` Fixes It

```java
public synchronized boolean reserve(int quantity) {
    if (getAvailableQuantity() >= quantity) {  // check
        reservedQuantity += quantity;           // set
        return true;
    }
    return false;
}
```

Thread B must wait until Thread A releases the lock. Thread B then sees `available = 2` and correctly returns `false`.

### Lock Granularity

```
Global lock (entire system)  → Correct but kills throughput
Warehouse-level lock         → Better, but unrelated products in same warehouse block each other
Inventory-level lock ✅      → Best. Each (product, warehouse) pair has its own lock.
                               iPhone-Mumbai and Laptop-Mumbai are independent.
```

**Interview line:** "I use inventory-level synchronized methods. Each Inventory object has its own lock. This means reserving iPhone in Mumbai doesn't block reserving Laptop in Mumbai. In production, I'd use database row-level locks (`SELECT FOR UPDATE`) or Redis atomic operations."

---

## 9. Edge Cases Handled

| Edge Case | How Handled | HTTP Response |
|-----------|------------|---------------|
| Create product with blank name | `@NotBlank` validation | 400: "name: must not be blank" |
| Create product with negative price | `@Positive` validation | 400: "price: must be greater than 0" |
| Get non-existent product | `ResourceNotFoundException` | 404: "Product not found with id: 99" |
| Add stock to non-existent warehouse | `ResourceNotFoundException` | 404: "Warehouse not found with id: 99" |
| Order with insufficient stock | Saga rollback + `BadRequestException` | 400: "Insufficient stock for product: X" |
| Multi-item order partial failure | ALL reservations rolled back (saga) | 400 + stock fully released |
| Confirm a non-PENDING order | State validation | 400: "Expected: PENDING, actual: CONFIRMED" |
| Cancel a DELIVERED order | State validation | 400: "Only PENDING orders can be cancelled" |
| Cancel an already cancelled order | Explicit check | 400: "Order is already cancelled" |
| Ship without confirming | State validation | 400: "Expected: CONFIRMED, actual: PENDING" |
| Deliver without shipping | State validation | 400: "Expected: SHIPPED, actual: CONFIRMED" |
| Empty order items list | `@NotEmpty` validation | 400: "items: must not be empty" |
| Concurrent order for last item | `synchronized` in Inventory | One succeeds, other gets 400 |

---

## 10. Order State Machine

```
                    ┌───────────┐
                    │  PENDING   │ ← placeOrder() (stock reserved)
                    └─────┬─────┘
                          │
              ┌───────────┼───────────┐
              │                       │
              ▼                       ▼
      ┌───────────┐          ┌────────────┐
      │ CONFIRMED │          │ CANCELLED  │ ← cancelOrder() (stock released)
      └─────┬─────┘          └────────────┘
            │
            ▼
      ┌───────────┐
      │  SHIPPED  │
      └─────┬─────┘
            │
            ▼
      ┌───────────┐
      │ DELIVERED │
      └───────────┘
```

**Valid transitions only:**
- PENDING → CONFIRMED (via confirm)
- PENDING → CANCELLED (via cancel)
- CONFIRMED → SHIPPED (via ship)
- SHIPPED → DELIVERED (via deliver)

**Any other transition → 400 Bad Request**

---

## 11. Stock Lifecycle

```
                restock(50)
               ┌────────────────────┐
               ▼                    │
  ┌──────────────────────────┐      │
  │   AVAILABLE              │      │
  │   (total - reserved)     │      │
  └──────────┬───────────────┘      │
             │ reserve(qty)         │
             ▼                      │
  ┌──────────────────────────┐      │
  │   RESERVED               │      │
  │   (claimed by order)     │      │
  └──────┬───────────┬───────┘      │
         │           │              │
  confirmStock   releaseStock       │
         │           │              │
         ▼           ▼              │
  ┌───────────┐  ┌──────────────────┘
  │  SOLD     │  │ Back to AVAILABLE
  │  (gone)   │  │ (order cancelled)
  └───────────┘  └──────────────────
```

| Operation | totalQuantity | reservedQuantity | availableQuantity |
|-----------|:---:|:---:|:---:|
| addStock(50) | +50 | 0 | +50 |
| reserve(10) | 50 | +10 | -10 |
| confirmStock(10) | -10 | -10 | (unchanged) |
| releaseStock(10) | (unchanged) | -10 | +10 |

---

## 12. Common Follow-Up Questions & Answers

| Interviewer Asks | Your Answer |
|-----------------|-------------|
| **"How would you add a database?"** | "Replace ConcurrentHashMap with Spring Data JPA repositories. Models get `@Entity` and `@Id`. Service logic stays identical. Inventory gets `@Version` for optimistic locking." |
| **"How would you handle split fulfillment?"** | "Currently one warehouse per item. I'd change `reserveStock()` to return `List<Pair<warehouseId, quantity>>` that splits across warehouses. OrderItem becomes a list of FulfillmentUnits." |
| **"What about authentication?"** | "Add Spring Security with JWT. Role-based: ADMIN can manage products/stock, CUSTOMER can place orders. Use `@PreAuthorize` on controllers." |
| **"How would you scale this?"** | "Move from in-memory to a database. Use Redis for hot inventory checks. For flash sales, use Redis DECR for atomic stock reservation. Deploy behind a load balancer." |
| **"What about pagination?"** | "Add `Pageable` parameter to GET list endpoints. Return `Page<T>` with `totalElements`, `totalPages`, `currentPage`. Spring Data handles this automatically with JPA." |
| **"How to handle returns?"** | "Add RETURN_REQUESTED and RETURNED statuses. ReturnService validates order is DELIVERED, then calls `inventoryService.addStock()` to return items to the originating warehouse." |
| **"What about reservation timeouts?"** | "Add `reservedUntil: LocalDateTime` to Inventory. A `@Scheduled` job runs every N minutes, finds expired reservations, releases them. Prevents cart-hoarders blocking stock forever." |
| **"How would you add search?"** | "Add `@RequestParam` filters on GET /products — name (contains), category, minPrice, maxPrice. In production, back with Elasticsearch for full-text search." |
| **"What about caching?"** | "Add Spring Cache (`@Cacheable`) on getById methods. Use Redis as cache provider. Invalidate on update/delete with `@CacheEvict`." |
| **"How to test this?"** | "Unit tests: mock services in controllers, test service logic with JUnit. Integration: `@SpringBootTest` with `MockMvc`. Thread safety: use `CountDownLatch` with multiple threads hitting reserve() simultaneously." |

---

## 13. What to Say in Last 5 Minutes

Proactively mention these extensions:

1. **"I'd add reservation TTL"** — Prevent indefinite stock hoarding with a `reservedUntil` timestamp + scheduled cleanup.
2. **"I'd add event sourcing"** — Instead of mutating quantities directly, append events (StockReserved, StockConfirmed). Current state = replay of events. Full audit trail.
3. **"I'd add distributed locking"** — Replace `synchronized` with Redis-based locks for multi-instance deployment.
4. **"I'd add an API rate limiter"** — Protect against abuse during flash sales using token bucket or sliding window.
5. **"I'd add idempotency keys"** — For POST /orders, accept a client-generated idempotency key to prevent duplicate orders on network retry.

---

## 14. Checklist Before Interview

### Environment Ready
- [ ] Java 17 installed (`java -version`)
- [ ] Gradle working (`./gradlew --version`)
- [ ] IDE open with this project loaded
- [ ] Auto-complete enabled, Copilot DISABLED
- [ ] Terminal open for `./gradlew bootRun`
- [ ] Postman/curl ready for testing

### Knowledge Check
- [ ] Can explain Reserve → Confirm → Release lifecycle
- [ ] Can explain saga rollback in placeOrder
- [ ] Can explain why ConcurrentHashMap over HashMap
- [ ] Can explain synchronized at Inventory level
- [ ] Can explain order state machine transitions
- [ ] Can explain price snapshotting in OrderItem
- [ ] Can explain GlobalExceptionHandler and HTTP status codes
- [ ] Can explain Builder, Saga, State Machine, Repository, DTO patterns
- [ ] Can explain what you'd add with more time (section 13)

### During the Round
- [ ] Read problem fully before writing code (2 min)
- [ ] Create enums first (they're dependencies)
- [ ] Create models with Lombok (not plain Java — save time)
- [ ] Use Java Records for DTOs (shows Java 17 knowledge)
- [ ] Test each API as you build, don't wait until end
- [ ] Use proper HTTP status codes (201, 400, 404, 409)
- [ ] Keep controllers thin — all logic in services

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.5 |
| Java | 17 |
| Build | Gradle 8.7 |
| Validation | Jakarta Bean Validation (`@NotBlank`, `@Positive`, `@Valid`) |
| Boilerplate | Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`) |
| Storage | In-memory (`ConcurrentHashMap`) |
| API Format | JSON with consistent `ApiResponse<T>` wrapper |

---

*Good luck with the interview! Remember: clean code > clever code. Working demo > perfect architecture.*
