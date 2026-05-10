# Beginner Guide — Inventory Management System

> **Read this file FIRST**, before the README.
> This explains what inventory management is, what LLD is, and walks you through
> every file in this project step-by-step — like a senior engineer sitting next to you.

---

## Part 1: What is Inventory Management? (Real World)

Think about what happens when you order a laptop on Amazon or Flipkart:

```
1. You search for "MacBook Pro"          → Product exists in a catalog
2. You see "In Stock"                    → Some warehouse has this item
3. You click "Buy Now"                   → System RESERVES one unit for you
4. You complete payment                  → System CONFIRMS and deducts stock
5. If you cancel before payment          → System RELEASES the reservation
6. If stock drops below 10               → Warehouse manager gets an ALERT
```

**That's it.** An Inventory Management System tracks:
- **What** products exist
- **Where** they're stored (which warehouse)
- **How many** are available, reserved, or sold
- **Who** is buying them

---

## Part 2: What is Low Level Design (LLD)?

LLD means taking a real-world system and breaking it into:

| Thing | What It Means | Example |
|-------|---------------|---------|
| **Classes** | The "nouns" — things that exist | Product, Warehouse, Order |
| **Fields** | What each thing knows about itself | Product has name, price |
| **Methods** | What each thing can do | Inventory can `reserve()`, `restock()` |
| **Relationships** | How things connect | An Order contains OrderItems |
| **Interfaces** | Contracts that define behavior | "Any warehouse selection algorithm must implement `selectWarehouse()`" |
| **Design Patterns** | Proven solutions to common problems | Strategy pattern, Observer pattern |

**The goal:** Write code that is **correct**, **extensible** (easy to add features), and **doesn't break** under concurrent use.

---

## Part 3: The Big Picture — How Our System is Organized

```
┌─────────────────────────────────────────────────────────┐
│                      Main.java                          │
│                  (Entry point — start here)              │
│                                                         │
│  Creates everything and demonstrates the full flow      │
└────────────────────┬────────────────────────────────────┘
                     │ uses
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  SERVICE LAYER                           │
│                                                         │
│  OrderService          InventoryService                 │
│  - place order         - reserve/release/confirm stock  │
│  - confirm order       - select warehouse (strategy)    │
│  - cancel order        - notify on low stock (observer) │
└────────────────────┬────────────────────────────────────┘
                     │ uses
                     ▼
┌─────────────────────────────────────────────────────────┐
│                   MODEL LAYER                            │
│                                                         │
│  User        Product        Warehouse                   │
│  Order       OrderItem      Inventory  (the core class) │
│  UserRole    Category       OrderStatus                 │
└─────────────────────────────────────────────────────────┘
```

**Read it top-down:**
- `Main.java` is the entry point — it creates users, products, warehouses, and places orders
- **Services** contain the business logic — "what happens when someone orders something"
- **Models** are the data — "what things exist and what do they know"

---

## Part 4: Understanding Each Class (Read in This Order)

### Step 1: Start with the simplest models

**File: `model/Category.java`** — Just an enum listing product types.
```java
public enum Category {
    ELECTRONICS, CLOTHING, GROCERY, FURNITURE, SPORTS
}
```
Nothing complex. A product belongs to one category.

**File: `model/UserRole.java`** — Who can do what.
```java
public enum UserRole {
    ADMIN,     // can manage products, warehouses, stock
    CUSTOMER   // can place and cancel orders
}
```

**File: `model/OrderStatus.java`** — The lifecycle of an order.
```java
public enum OrderStatus {
    PENDING,     // stock reserved, waiting for payment
    CONFIRMED,   // payment done, stock permanently deducted
    SHIPPED,
    DELIVERED,
    CANCELLED    // stock released back
}
```

These three enums define the "vocabulary" of the system.

---

### Step 2: The core data classes

**File: `model/Product.java`** — A thing you can buy.
```
Product
├── id: "P001"
├── name: "MacBook Pro"
├── category: ELECTRONICS
├── price: 2499.99
└── description: "16-inch M3 Max"
```
Simple data holder. `id` and `name` are immutable (set once, never change).
`price` can be updated (admin might change pricing).

**File: `model/User.java`** — A person using the system.
```
User
├── id: "U001"
├── name: "Ravi"
├── email: "ravi@company.com"
├── role: ADMIN
├── canManageInventory() → true  (because ADMIN)
└── canPlaceOrder() → false      (only CUSTOMER can order)
```
The `canManageInventory()` and `canPlaceOrder()` methods enforce who can do what.

---

### Step 3: The MOST IMPORTANT class — Inventory

**File: `model/Inventory.java`**

This is the heart of the entire system. One Inventory object tracks stock for
**one product** in **one warehouse**.

```
Example: "MacBook Pro" in "Bangalore Warehouse"

Inventory
├── product: MacBook Pro
├── totalQuantity: 100     ← physically present in warehouse
├── reservedQuantity: 15   ← claimed by pending orders (not yet shipped)
└── available: 85          ← what new orders can claim (100 - 15)
```

**Why three numbers instead of one?**

Imagine you only tracked one number (`quantity = 100`):

```
Customer A clicks "Buy" at 10:00:01  → quantity = 99
Customer A hasn't paid yet...
Customer B clicks "Buy" at 10:00:02  → quantity = 98
Customer A cancels at 10:00:05       → quantity = 99? 100? What's correct?
```

With reservation tracking, it's clean:

```
Initial:         total=100, reserved=0,  available=100
Customer A buys: total=100, reserved=1,  available=99   ← reserve()
Customer B buys: total=100, reserved=2,  available=98   ← reserve()
Customer A pays: total=99,  reserved=1,  available=98   ← confirmReservation()
Customer B cancels: total=99, reserved=0, available=99  ← releaseReservation()
```

Every number is always correct. No ambiguity.

**The four operations on Inventory:**

```
┌─────────────────────┬──────────────────────────────────────┐
│ Method              │ What happens                         │
├─────────────────────┼──────────────────────────────────────┤
│ reserve(qty)        │ reservedQty += qty                   │
│                     │ (available goes DOWN)                │
├─────────────────────┼──────────────────────────────────────┤
│ confirmReservation  │ reservedQty -= qty                   │
│ (qty)               │ totalQty -= qty                      │
│                     │ (item shipped, gone from warehouse)  │
├─────────────────────┼──────────────────────────────────────┤
│ releaseReservation  │ reservedQty -= qty                   │
│ (qty)               │ (available goes back UP)             │
├─────────────────────┼──────────────────────────────────────┤
│ restock(qty)        │ totalQty += qty                      │
│                     │ (new shipment arrived at warehouse)  │
└─────────────────────┴──────────────────────────────────────┘
```

---

### Step 4: Warehouse — holds multiple Inventory records

**File: `model/Warehouse.java`**

```
Warehouse: "Bangalore Hub"
├── id: "WH01"
├── name: "Bangalore Hub"
├── address: "Whitefield, Bangalore"
└── inventoryMap:
    ├── "P001" → Inventory{MacBook Pro, total=50, reserved=3, available=47}
    ├── "P002" → Inventory{iPhone 15, total=200, reserved=0, available=200}
    └── "P003" → Inventory{Polo Shirt, total=500, reserved=12, available=488}
```

A warehouse is basically a **map** from product ID to its inventory record.
You can ask: "Does Bangalore Hub have 5 MacBooks available?" → `hasStock("P001", 5)`

---

### Step 5: Order and OrderItem

**File: `model/OrderItem.java`** — One line in your shopping cart.
```
OrderItem
├── product: MacBook Pro
├── quantity: 2
├── unitPrice: 2499.99 (captured at time of order)
├── fulfilledFrom: Bangalore Hub
└── subtotal: 4999.98 (2 × 2499.99)
```

**Why `unitPrice` instead of just using `product.getPrice()`?**

Product prices change over time. An admin might reduce the MacBook price next week.
But if Priya ordered it at $2499, her order must still show $2499 — not the new price.

```
Monday:    MacBook price = $2499     Priya orders 1 MacBook
Wednesday: Admin changes price to $2299
Thursday:  Priya checks order → should show $2499, NOT $2299

If OrderItem used product.getPrice():  subtotal = $2299  ← WRONG (billing bug!)
If OrderItem uses unitPrice:           subtotal = $2499  ← CORRECT (captured at order time)
```

This is called **price snapshotting**. The `unitPrice` field freezes the price
at the moment the order is created, so future price changes never affect past orders.

**File: `model/Order.java`** — The full order.
```
Order
├── id: "ORD-1"
├── customer: User{Priya}
├── status: PENDING
├── createdAt: 2026-04-19T10:30:00
└── items:
    ├── OrderItem{MacBook Pro, qty=2, from Bangalore Hub}
    └── OrderItem{iPhone 15, qty=1, from Mumbai Hub}
```

---

### Step 6: Now the interesting parts — Strategy and Observer

These are "design patterns" — think of them as **recipes** for solving common problems.

#### Strategy Pattern — "How to pick a warehouse"

**The problem:** When a customer orders a MacBook, and both Bangalore and Mumbai
have stock, which warehouse should fulfill it?

There's no single right answer. It depends:
- Pick the **nearest** warehouse → faster delivery
- Pick the warehouse with **most stock** → balanced load
- Pick **round-robin** → even distribution

**The solution:** Instead of hardcoding the logic, define a **contract**:

```
File: strategy/WarehouseSelectionStrategy.java (the contract)

"Any warehouse selection algorithm must implement this one method:
 selectWarehouse(product, quantity, listOfWarehouses) → returns one Warehouse"
```

Then each algorithm is a separate class:

```
File: strategy/NearestWarehouseStrategy.java
  → Loops through warehouses, returns the first one with enough stock

File: strategy/MaxStockWarehouseStrategy.java
  → Loops through warehouses, returns the one with the MOST stock
```

**Why is this useful?** The InventoryService doesn't know or care WHICH strategy
is being used. You can swap strategies without changing any other code:

```java
// Morning: use nearest warehouse (fast delivery)
inventoryService.setSelectionStrategy(new NearestWarehouseStrategy());

// Flash sale: use max-stock warehouse (prevent one warehouse from running out)
        inventoryService.setSelectionStrategy(new MaxStockWarehouseStrategy());
```

#### Observer Pattern — "What happens when stock is low"

**The problem:** When MacBook stock drops below 10, we want to:
- Log an alert
- Maybe auto-restock

But InventoryService shouldn't know about logging or restocking. Those are
different concerns.

**The solution:** Define a **contract** for "something that cares about low stock":

```
File: observer/StockObserver.java (the contract)

"Anything that wants to react to low stock must implement:
 onLowStock(product, warehouse, currentQuantity)"
```

Then each reaction is a separate class:

```
File: observer/LowStockAlertObserver.java
  → Prints a warning message

File: observer/RestockObserver.java
  → Automatically adds more stock to the warehouse
```

**How they connect:** InventoryService maintains a list of observers.
When stock drops below threshold, it tells ALL of them:

```java
// Register observers (done once at startup)
inventoryService.registerObserver(new LowStockAlertObserver());
        inventoryService.registerObserver(new RestockObserver(50));

// Later, when someone reserves stock and it drops below 10:
// InventoryService automatically calls:
//   alertObserver.onLowStock(macbook, bangaloreHub, 5);
//   restockObserver.onLowStock(macbook, bangaloreHub, 5);
```

---

### Step 7: The Service Layer — where business logic lives

**File: `service/InventoryService.java`** — The brain of inventory operations.

It connects everything together:
```
InventoryService
├── knows all warehouses    (Map of warehouseId → Warehouse)
├── knows all products      (Map of productId → Product)
├── has a strategy          (how to pick warehouse)
├── has observers           (what to do on low stock)
│
├── addStock(warehouseId, productId, qty)
│     → finds the warehouse, finds the product, adds stock
│
├── reserveStock(productId, qty) → returns Warehouse
│     → asks strategy to pick a warehouse
│     → calls inventory.reserve(qty)
│     → if stock is low, notifies observers
│     → returns which warehouse it reserved from
│
├── confirmStock(warehouseId, productId, qty)
│     → calls inventory.confirmReservation(qty)
│
└── releaseStock(warehouseId, productId, qty)
      → calls inventory.releaseReservation(qty)
```

**File: `service/OrderService.java`** — Manages order lifecycle.

```
OrderService
├── has an InventoryService (delegates stock operations to it)
├── knows all orders        (Map of orderId → Order)
│
├── placeOrder(user, {productA: 2, productB: 3}) → Order
│     → checks user is CUSTOMER
│     → for each item: calls inventoryService.reserveStock()
│     → if any item fails: ROLLS BACK all previous reservations
│     → creates Order with status PENDING
│
├── confirmOrder(orderId)
│     → checks status is PENDING
│     → for each item: calls inventoryService.confirmStock()
│     → sets status to CONFIRMED
│
└── cancelOrder(orderId)
      → checks status is PENDING
      → for each item: calls inventoryService.releaseStock()
      → sets status to CANCELLED
```

---

## Part 5: Trace the Complete Flow — "What happens when Priya orders 2 MacBooks?"

Open `Main.java` and follow along. Here's every step:

### Phase 1: System Setup (Main.java lines ~20-30)

```
1. Create a NearestWarehouseStrategy        ← the algorithm for picking warehouses
2. Create InventoryService(strategy, 10)    ← 10 is the low-stock threshold
3. Create OrderService(inventoryService)    ← OrderService uses InventoryService
4. Register two observers:
   - LowStockAlertObserver   → prints warnings
   - RestockObserver(50)     → auto-adds 50 units on low stock
```

**What we have so far:**
```
OrderService → InventoryService → Strategy (NearestWarehouse)
                                → Observers [AlertObserver, RestockObserver]
```

### Phase 2: Create Data (Main.java lines ~33-55)

```
5. Create User: Priya (CUSTOMER)
6. Create Products: MacBook (P001), iPhone (P002), Polo Shirt (P003)
7. Create Warehouses: Bangalore (WH01), Mumbai (WH02)
8. Add stock:
   - Bangalore: 15 MacBooks, 100 iPhones, 200 Shirts
   - Mumbai: 50 MacBooks, 80 iPhones
```

**Memory state after setup:**
```
Bangalore Hub                      Mumbai Hub
├── MacBook:  total=15, avail=15   ├── MacBook:  total=50, avail=50
├── iPhone:   total=100, avail=100 ├── iPhone:   total=80, avail=80
└── Shirt:    total=200, avail=200
```

### Phase 3: Priya Places Order — 2 MacBooks + 3 iPhones

```
9.  Priya calls: orderService.placeOrder(priya, {P001: 2, P002: 3})
    │
    ├── OrderService checks: priya.canPlaceOrder()? YES (she's CUSTOMER)
    │
    ├── Item 1: reserveStock("P001", 2)
    │   ├── InventoryService asks Strategy: selectWarehouse(MacBook, 2, [Bangalore, Mumbai])
    │   ├── NearestWarehouseStrategy checks Bangalore: available=15 >= 2? YES → picks Bangalore
    │   ├── Calls bangalore.getInventory("P001").reserve(2)
    │   │   └── synchronized: available=15 >= 2? YES → reservedQty = 0+2 = 2
    │   ├── Check: available (13) < threshold (10)? NO → no alert
    │   └── Returns: Bangalore Hub
    │
    ├── Item 2: reserveStock("P002", 3)
    │   ├── Strategy picks Bangalore again (first with enough stock)
    │   ├── Calls bangalore.getInventory("P002").reserve(3)
    │   │   └── synchronized: available=100 >= 3? YES → reservedQty = 0+3 = 3
    │   └── Returns: Bangalore Hub
    │
    ├── All items reserved successfully!
    ├── Creates: Order{id="ORD-1", customer=Priya, status=PENDING, items=[...]}
    └── Returns Order to Priya
```

**Memory state after order:**
```
Bangalore Hub
├── MacBook:  total=15,  reserved=2, available=13
├── iPhone:   total=100, reserved=3, available=97
└── Shirt:    total=200, reserved=0, available=200
```

### Phase 4: Order Confirmed (Payment Received)

```
10. orderService.confirmOrder("ORD-1")
    │
    ├── Checks: order.status == PENDING? YES
    │
    ├── Item 1: confirmStock("WH01", "P001", 2)
    │   └── inventory.confirmReservation(2)
    │       └── reservedQty = 2-2 = 0, totalQty = 15-2 = 13
    │
    ├── Item 2: confirmStock("WH01", "P002", 3)
    │   └── inventory.confirmReservation(3)
    │       └── reservedQty = 3-3 = 0, totalQty = 100-3 = 97
    │
    └── order.status = CONFIRMED
```

**Memory state after confirmation:**
```
Bangalore Hub
├── MacBook:  total=13,  reserved=0, available=13    ← 2 permanently gone
├── iPhone:   total=97,  reserved=0, available=97    ← 3 permanently gone
└── Shirt:    total=200, reserved=0, available=200
```

### Phase 5: Amit Orders 8 MacBooks (Triggers Low Stock!)

```
11. orderService.placeOrder(amit, {P001: 8})
    │
    ├── reserveStock("P001", 8)
    │   ├── Strategy picks Bangalore: available=13 >= 8? YES
    │   ├── reserve(8) → reservedQty = 0+8 = 8, available = 13-8 = 5
    │   ├── Check: available (5) < threshold (10)? YES!
    │   │
    │   ├── NOTIFY OBSERVERS:
    │   │   ├── LowStockAlertObserver.onLowStock(MacBook, Bangalore, 5)
    │   │   │   └── Prints: "[ALERT] Low stock: 'MacBook Pro' — only 5 left"
    │   │   │
    │   │   └── RestockObserver.onLowStock(MacBook, Bangalore, 5)
    │   │       └── Calls: bangalore.addProduct(macbook, 50)
    │   │           └── totalQty = 13 + 50 = 63
    │   │
    │   └── Returns: Bangalore Hub
    │
    └── Creates Order{id="ORD-2", status=PENDING}
```

**Memory state after (notice the auto-restock!):**
```
Bangalore Hub
├── MacBook:  total=63, reserved=8, available=55    ← was 13, restocked +50!
```

### Phase 6: Amit Cancels (Stock Released)

```
12. orderService.cancelOrder("ORD-2")
    │
    ├── releaseStock("WH01", "P001", 8)
    │   └── inventory.releaseReservation(8)
    │       └── reservedQty = 8-8 = 0 (totalQty stays 63)
    │
    └── order.status = CANCELLED
```

**Memory state after cancellation:**
```
Bangalore Hub
├── MacBook:  total=63, reserved=0, available=63    ← 8 released back
```

### Phase 7: Rollback Demo — What if an Order Partially Fails?

```
13. Priya orders: {P002: 5, P001: 999}
    │
    ├── Item 1: reserveStock("P002", 5) → Bangalore reserves 5 phones ✓
    │
    ├── Item 2: reserveStock("P001", 999) → No warehouse has 999 MacBooks → null
    │
    ├── FAILURE! Must rollback Item 1.
    │   └── releaseStock("WH01", "P002", 5) → Bangalore releases 5 phones
    │
    └── Throws InsufficientStockException("P001")

    Phone stock is unchanged — as if the order never happened. This is the saga rollback.
```

---

## Part 6: How to Read the Code Files

### Recommended reading order:

```
START HERE
    │
    ▼
1.  model/Category.java          ← 10 lines, just an enum
2.  model/UserRole.java           ← 4 lines, just an enum
3.  model/OrderStatus.java        ← 8 lines, just an enum
    │
    ▼
4.  model/Product.java            ← simple data class
5.  model/User.java               ← simple data class with role check
    │
    ▼
6.  model/Inventory.java          ← ★ THE MOST IMPORTANT FILE ★
    │                                Read every method carefully.
    │                                This is the core of the system.
    ▼
7.  model/Warehouse.java          ← holds a map of Inventory objects
    │
    ▼
8.  model/OrderItem.java          ← one line item in an order
9.  model/Order.java              ← groups items + status + customer
    │
    ▼
10. strategy/WarehouseSelectionStrategy.java  ← interface (the contract)
11. strategy/NearestWarehouseStrategy.java    ← one implementation
12. strategy/MaxStockWarehouseStrategy.java   ← another implementation
    │
    ▼
13. observer/StockObserver.java              ← interface (the contract)
14. observer/LowStockAlertObserver.java      ← one implementation
15. observer/RestockObserver.java            ← another implementation
    │
    ▼
16. service/InventoryService.java  ← ★ SECOND MOST IMPORTANT ★
    │                                 Connects strategy, observers, and inventory
    ▼
17. service/OrderService.java      ← Order lifecycle + saga rollback
    │
    ▼
18. Main.java                      ← ★ RUN THIS to see everything in action
```

### To compile and run:
```bash
cd src/
javac -d ../out model/*.java strategy/*.java observer/*.java exception/*.java service/*.java Main.java
cd ../out
java Main
```

---

## Part 7: Key LLD Concepts Summarized

### What is a Design Pattern?
A **proven recipe** for a common problem. Like how a recipe for "how to make bread"
works across kitchens — a design pattern works across codebases.

### What is the Strategy Pattern?
When you have **multiple algorithms** for the same task, put each behind an interface.
Swap them without changing the code that uses them.

**Real-world analogy:** Google Maps gives you multiple route options — shortest, fastest,
avoid tolls. The navigation system works the same regardless of which route algorithm
you picked.

### What is the Observer Pattern?
When **one event** needs to trigger **multiple reactions** from different parts of
the system, without those parts knowing about each other.

**Real-world analogy:** When you post on Instagram, your followers get notified,
the post appears in feeds, analytics are updated — but the "post" action doesn't
know about any of these systems. They're all "observers" of the "new post" event.

### What is Thread Safety?
When **two things happen at the same time** and share data, you need to make sure
they don't corrupt it.

**Real-world analogy:** Two people reaching for the last cookie at the same time.
Without rules, both grab it and it crumbles. With a rule ("only one person can
reach into the jar at a time"), one gets the cookie and the other sees it's empty.
That "rule" is `synchronized` in Java.

### What is the Saga Pattern?
When an operation has **multiple steps** and any step can fail, you need to **undo**
the successful steps. Like a bank transfer: if debit succeeds but credit fails,
you must reverse the debit.

---

## Part 8: Now You're Ready

1. You've read this file — you understand what inventory management is and how the code fits together
2. **Next:** Open `Main.java`, run it, and watch the output
3. **Then:** Read the README.md for the interview-specific content (how to present this in 45 minutes, what questions to ask, trade-offs, follow-up answers)

Good luck with the interview!
