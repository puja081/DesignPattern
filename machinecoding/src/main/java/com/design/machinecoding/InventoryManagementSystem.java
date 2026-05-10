package com.design.machinecoding;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;


// ┌──────────────────────────────────────────────────────────────────────┐
// │                                                                      │
// │   MACHINE CODING — Inventory Management System                       │
// │   Single file. Compiles. Runs. Shows output.                         │
// │                                                                      │
// │   HOW TO USE THIS FILE:                                              │
// │   • PHASE 1 (MUST HAVE)  → Write this in first 30 min               │
// │   • PHASE 2 (NICE TO HAVE) → Add this in next 30 min                │
// │   • PHASE 1 alone gives you a working system                         │
// │   • PHASE 2 shows senior-level thinking                              │
// │                                                                      │
// │   Compile & Run:                                                     │
// │     javac InventoryManagementSystem.java && java InventoryManagement │
// │     System                                                           │
// │                                                                      │
// └──────────────────────────────────────────────────────────────────────┘


// ════════════════════════════════════════════════════════════════════════
//  PHASE 1: MUST HAVE  (write this first — ~30 minutes)
//  Without this, your submission fails.
// ════════════════════════════════════════════════════════════════════════


// ── 1a. Enums ──────────────────────────────────────────────────────────

enum Category {
    ELECTRONICS, CLOTHING, GROCERY, FURNITURE, SPORTS
}

// ── 1b. Product ────────────────────────────────────────────────────────

class Product {
    private final String id;
    private final String name;
    private final Category category;
    private double price;

    Product(String id, String name, Category category, double price) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    String getId()       { return id; }
    String getName()     { return name; }
    Category getCategory() { return category; }
    double getPrice()    { return price; }
    void setPrice(double price) { this.price = price; }

    @Override
    public String toString() {
        return name + " ($" + price + ")";
    }
}

// ── 1c. Inventory (CORE CLASS) ────────────────────────────────────────
//    Tracks stock for ONE product in ONE warehouse.
//    Three numbers: total, reserved, available = total - reserved

class Inventory {
    private final Product product;
    private int totalQuantity;
    private int reservedQuantity;

    Inventory(Product product, int initialQuantity) {
        this.product = product;
        this.totalQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    Product getProduct() { return product; }

    synchronized int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    // Customer clicks "Buy" → reserve stock
    synchronized boolean reserve(int qty) {
        if (getAvailableQuantity() >= qty) {
            reservedQuantity += qty;
            return true;
        }
        return false;
    }

    // Payment confirmed → permanently remove stock
    synchronized void confirmReservation(int qty) {
        reservedQuantity -= qty;
        totalQuantity -= qty;
    }

    // Order cancelled → put reserved stock back
    synchronized void releaseReservation(int qty) {
        reservedQuantity -= qty;
    }

    // New shipment arrives → add stock
    synchronized void restock(int qty) {
        totalQuantity += qty;
    }

    @Override
    public String toString() {
        return String.format("  %-15s total=%-4d reserved=%-4d available=%-4d",
                product.getName(), totalQuantity, reservedQuantity, getAvailableQuantity());
    }
}

// ── 1d. Warehouse ──────────────────────────────────────────────────────
//    Holds a map of productId → Inventory

class Warehouse {
    private final String id;
    private final String name;
    private final Map<String, Inventory> inventoryMap = new ConcurrentHashMap<>();

    Warehouse(String id, String name) {
        this.id = id;
        this.name = name;
    }

    String getId()   { return id; }
    String getName() { return name; }

    void addProduct(Product product, int qty) {
        inventoryMap.putIfAbsent(product.getId(), new Inventory(product, 0));
        inventoryMap.get(product.getId()).restock(qty);
    }

    Inventory getInventory(String productId) {
        return inventoryMap.get(productId);
    }

    boolean hasStock(String productId, int qty) {
        Inventory inv = inventoryMap.get(productId);
        return inv != null && inv.getAvailableQuantity() >= qty;
    }

    void printStock() {
        System.out.println("[" + name + "]");
        for (Inventory inv : inventoryMap.values()) {
            System.out.println(inv);
        }
    }
}

// ── 1e. InventoryService (basic version) ───────────────────────────────
//    In Phase 1: simple loop to find a warehouse (no strategy pattern yet)

class InventoryService {
    private final Map<String, Warehouse> warehouses = new ConcurrentHashMap<>();
    private final Map<String, Product> products = new ConcurrentHashMap<>();

    // ── PHASE 2 additions (added later, but declared here so code compiles) ──
    private WarehouseSelectionStrategy strategy = null;
    private final List<StockObserver> observers = new ArrayList<>();
    private int lowStockThreshold = 10;

    void addWarehouse(Warehouse w)  { warehouses.put(w.getId(), w); }
    void addProduct(Product p)      { products.put(p.getId(), p); }
    Product getProduct(String id)   { return products.get(id); }

    void addStock(String warehouseId, String productId, int qty) {
        Warehouse w = warehouses.get(warehouseId);
        Product p = products.get(productId);
        w.addProduct(p, qty);
    }

    // Reserve stock — picks a warehouse and reserves
    Warehouse reserveStock(String productId, int qty) {
        Product product = products.get(productId);
        if (product == null) return null;

        // If strategy exists (Phase 2), use it. Otherwise simple loop (Phase 1).
        Warehouse selected;
        if (strategy != null) {
            selected = strategy.selectWarehouse(product, qty, new ArrayList<>(warehouses.values()));
        } else {
            selected = findFirstAvailableWarehouse(productId, qty);
        }

        if (selected == null) return null;

        Inventory inv = selected.getInventory(productId);
        if (!inv.reserve(qty)) return null;

        // Phase 2: notify observers if stock is low
        if (!observers.isEmpty() && inv.getAvailableQuantity() < lowStockThreshold) {
            for (StockObserver obs : observers) {
                obs.onLowStock(product, selected, inv.getAvailableQuantity());
            }
        }

        return selected;
    }

    void confirmStock(String warehouseId, String productId, int qty) {
        warehouses.get(warehouseId).getInventory(productId).confirmReservation(qty);
    }

    void releaseStock(String warehouseId, String productId, int qty) {
        Warehouse w = warehouses.get(warehouseId);
        if (w == null) return;
        Inventory inv = w.getInventory(productId);
        if (inv == null) return;
        inv.releaseReservation(qty);
    }

    // Phase 1: simple warehouse lookup
    private Warehouse findFirstAvailableWarehouse(String productId, int qty) {
        for (Warehouse w : warehouses.values()) {
            if (w.hasStock(productId, qty)) return w;
        }
        return null;
    }

    // Phase 2 setters
    void setStrategy(WarehouseSelectionStrategy s) { this.strategy = s; }
    void setLowStockThreshold(int t) { this.lowStockThreshold = t; }
    void registerObserver(StockObserver o) { observers.add(o); }
}


// ════════════════════════════════════════════════════════════════════════
//  PHASE 2: NICE TO HAVE  (add this if time permits — next ~30 minutes)
//  This is what separates a senior candidate from a mid-level one.
// ════════════════════════════════════════════════════════════════════════


// ── 2a. Strategy Pattern — pluggable warehouse selection ───────────────

interface WarehouseSelectionStrategy {
    Warehouse selectWarehouse(Product product, int qty, List<Warehouse> warehouses);
}

class NearestWarehouseStrategy implements WarehouseSelectionStrategy {
    @Override
    public Warehouse selectWarehouse(Product product, int qty, List<Warehouse> warehouses) {
        for (Warehouse w : warehouses) {
            if (w.hasStock(product.getId(), qty)) return w;
        }
        return null;
    }
}

class MaxStockWarehouseStrategy implements WarehouseSelectionStrategy {
    @Override
    public Warehouse selectWarehouse(Product product, int qty, List<Warehouse> warehouses) {
        Warehouse best = null;
        int maxStock = 0;
        for (Warehouse w : warehouses) {
            Inventory inv = w.getInventory(product.getId());
            if (inv != null && inv.getAvailableQuantity() >= qty) {
                if (inv.getAvailableQuantity() > maxStock) {
                    maxStock = inv.getAvailableQuantity();
                    best = w;
                }
            }
        }
        return best;
    }
}

// ── 2b. Observer Pattern — low stock notifications ─────────────────────

interface StockObserver {
    void onLowStock(Product product, Warehouse warehouse, int currentQty);
}

class LowStockAlertObserver implements StockObserver {
    @Override
    public void onLowStock(Product product, Warehouse warehouse, int currentQty) {
        System.out.printf("    ⚠ ALERT: '%s' in [%s] has only %d left!%n",
                product.getName(), warehouse.getName(), currentQty);
    }
}

// ── 2c. Order lifecycle ────────────────────────────────────────────────

enum OrderStatus {
    PENDING, CONFIRMED, CANCELLED
}

class OrderItem {
    final Product product;
    final int quantity;
    final double unitPrice; // snapshot of price at order time — not affected by future price changes
    final Warehouse fulfilledFrom;

    OrderItem(Product product, int quantity, Warehouse fulfilledFrom) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.fulfilledFrom = fulfilledFrom;
    }

    double getSubtotal() { return unitPrice * quantity; }

    @Override
    public String toString() {
        return String.format("  %s x%d @ $%.2f from [%s] = $%.2f",
                product.getName(), quantity, unitPrice, fulfilledFrom.getName(), getSubtotal());
    }
}

class Order {
    private final String id;
    private final List<OrderItem> items;
    private OrderStatus status;

    Order(String id, List<OrderItem> items) {
        this.id = id;
        this.items = items;
        this.status = OrderStatus.PENDING;
    }

    String getId()              { return id; }
    List<OrderItem> getItems()  { return items; }
    OrderStatus getStatus()     { return status; }
    void setStatus(OrderStatus s) { this.status = s; }

    double getTotal() {
        return items.stream().mapToDouble(OrderItem::getSubtotal).sum();
    }

    @Override
    public String toString() {
        return String.format("Order{%s, status=%s, items=%d, total=$%.2f}", id, status, items.size(), getTotal());
    }
}

// ── 2d. OrderService — with saga rollback ──────────────────────────────

class OrderService {
    private final InventoryService inventoryService;
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    OrderService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    Order placeOrder(Map<String, Integer> items) {
        List<OrderItem> reserved = new ArrayList<>();

        try {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                String productId = entry.getKey();
                int qty = entry.getValue();

                Warehouse wh = inventoryService.reserveStock(productId, qty);
                if (wh == null) {
                    throw new RuntimeException("Insufficient stock for: " + productId);
                }
                reserved.add(new OrderItem(inventoryService.getProduct(productId), qty, wh));
            }
        } catch (RuntimeException e) {
            // SAGA ROLLBACK: undo all successful reservations
            for (OrderItem item : reserved) {
                inventoryService.releaseStock(
                        item.fulfilledFrom.getId(), item.product.getId(), item.quantity);
            }
            throw e;
        }

        String orderId = "ORD-" + counter.incrementAndGet();
        Order order = new Order(orderId, reserved);
        orders.put(orderId, order);
        return order;
    }

    void confirmOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed");
        }
        for (OrderItem item : order.getItems()) {
            inventoryService.confirmStock(
                    item.fulfilledFrom.getId(), item.product.getId(), item.quantity);
        }
        order.setStatus(OrderStatus.CONFIRMED);
    }

    void cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be cancelled");
        }
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(
                    item.fulfilledFrom.getId(), item.product.getId(), item.quantity);
        }
        order.setStatus(OrderStatus.CANCELLED);
    }
}


// ════════════════════════════════════════════════════════════════════════
//  MAIN — The entry point. Demonstrates everything.
// ════════════════════════════════════════════════════════════════════════

public class InventoryManagementSystem {

    public static void main(String[] args) {

        // ────────────────────────────────────────────────────────
        //  PHASE 1 DEMO: Core inventory operations
        //  (This alone is a passing submission)
        // ────────────────────────────────────────────────────────

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║  PHASE 1: Core Inventory Operations ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        InventoryService inventoryService = new InventoryService();

        // Create products
        Product laptop = new Product("P001", "MacBook Pro", Category.ELECTRONICS, 2499.99);
        Product phone  = new Product("P002", "iPhone 15",   Category.ELECTRONICS, 999.99);
        Product shirt  = new Product("P003", "Polo Shirt",  Category.CLOTHING,    49.99);

        inventoryService.addProduct(laptop);
        inventoryService.addProduct(phone);
        inventoryService.addProduct(shirt);

        // Create warehouses
        Warehouse whBangalore = new Warehouse("WH01", "Bangalore");
        Warehouse whMumbai    = new Warehouse("WH02", "Mumbai");

        inventoryService.addWarehouse(whBangalore);
        inventoryService.addWarehouse(whMumbai);

        // Stock them
        inventoryService.addStock("WH01", "P001", 15);
        inventoryService.addStock("WH01", "P002", 100);
        inventoryService.addStock("WH02", "P001", 50);
        inventoryService.addStock("WH02", "P002", 80);

        System.out.println("Initial stock:");
        whBangalore.printStock();
        whMumbai.printStock();

        // Reserve stock (customer clicks "Buy")
        System.out.println("\n→ Reserve 3 MacBooks...");
        Warehouse selected = inventoryService.reserveStock("P001", 3);
        System.out.println("  Reserved from: " + selected.getName());
        whBangalore.printStock();

        // Confirm (payment done)
        System.out.println("\n→ Confirm reservation (payment done)...");
        inventoryService.confirmStock("WH01", "P001", 3);
        whBangalore.printStock();

        // Reserve and then cancel
        System.out.println("\n→ Reserve 5 iPhones, then cancel...");
        inventoryService.reserveStock("P002", 5);
        System.out.println("  After reserve:");
        whBangalore.printStock();

        inventoryService.releaseStock("WH01", "P002", 5);
        System.out.println("  After cancel (stock returned):");
        whBangalore.printStock();

        // Insufficient stock
        System.out.println("\n→ Try to reserve 999 MacBooks (should fail)...");
        Warehouse result = inventoryService.reserveStock("P001", 999);
        System.out.println("  Result: " + (result == null ? "FAILED — not enough stock" : result.getName()));

        System.out.println("\n✓ Phase 1 complete. Core operations work.\n");

        // ────────────────────────────────────────────────────────
        //  PHASE 2 DEMO: Strategy + Observer + Orders + Rollback
        //  (Add this to impress the interviewer)
        // ────────────────────────────────────────────────────────

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║  PHASE 2: Patterns + Order Lifecycle ║");
        System.out.println("╚══════════════════════════════════════╝\n");

        // 2A: Plug in Strategy pattern
        System.out.println("--- Strategy Pattern ---");
        inventoryService.setStrategy(new NearestWarehouseStrategy());
        System.out.println("Using NearestWarehouseStrategy:");

        Warehouse picked = inventoryService.reserveStock("P001", 2);
        System.out.println("  Picked: " + picked.getName());
        inventoryService.releaseStock(picked.getId(), "P001", 2);

        inventoryService.setStrategy(new MaxStockWarehouseStrategy());
        System.out.println("Switched to MaxStockWarehouseStrategy:");

        picked = inventoryService.reserveStock("P001", 2);
        System.out.println("  Picked: " + picked.getName() + " (has more stock)");
        inventoryService.releaseStock(picked.getId(), "P001", 2);

        // 2B: Observer pattern
        System.out.println("\n--- Observer Pattern ---");
        inventoryService.setStrategy(new NearestWarehouseStrategy());
        inventoryService.setLowStockThreshold(8);
        inventoryService.registerObserver(new LowStockAlertObserver());

        System.out.println("Reserving 8 MacBooks from Bangalore (only 12 left, threshold=8):");
        inventoryService.reserveStock("P001", 8);
        inventoryService.releaseStock("WH01", "P001", 8);

        // 2C: Full order lifecycle
        System.out.println("\n--- Order Lifecycle ---");
        OrderService orderService = new OrderService(inventoryService);

        Map<String, Integer> cart = new LinkedHashMap<>();
        cart.put("P001", 2);
        cart.put("P002", 3);

        Order order1 = orderService.placeOrder(cart);
        System.out.println("Placed: " + order1);
        for (OrderItem item : order1.getItems()) System.out.println(item);

        orderService.confirmOrder(order1.getId());
        System.out.println("After confirm: " + order1);
        whBangalore.printStock();

        // 2D: Order cancellation
        System.out.println("\n--- Order Cancellation ---");
        Map<String, Integer> cart2 = new LinkedHashMap<>();
        cart2.put("P002", 10);

        Order order2 = orderService.placeOrder(cart2);
        System.out.println("Placed: " + order2);
        System.out.println("Stock after reservation:");
        whBangalore.printStock();

        orderService.cancelOrder(order2.getId());
        System.out.println("After cancel: " + order2);
        System.out.println("Stock restored:");
        whBangalore.printStock();

        // 2E: Saga rollback
        System.out.println("\n--- Saga Rollback ---");
        System.out.println("Ordering 5 iPhones + 999 MacBooks (second will fail):");
        try {
            Map<String, Integer> badCart = new LinkedHashMap<>();
            badCart.put("P002", 5);
            badCart.put("P001", 999);
            orderService.placeOrder(badCart);
        } catch (RuntimeException e) {
            System.out.println("  Error: " + e.getMessage());
            System.out.println("  iPhone stock unchanged (rollback worked):");
            whBangalore.printStock();
        }

        // 2F: Price snapshot — unitPrice protects past orders from price changes
        System.out.println("\n--- Price Snapshot (unitPrice use case) ---");
        System.out.println("MacBook current price: $" + laptop.getPrice());

        Map<String, Integer> snapCart = new LinkedHashMap<>();
        snapCart.put("P001", 1);
        Order order3 = orderService.placeOrder(snapCart);
        System.out.println("Order placed at $" + laptop.getPrice() + " → total: $"
                + String.format("%.2f", order3.getTotal()));

        laptop.setPrice(1999.99);
        System.out.println("Admin changes MacBook price to: $" + laptop.getPrice());
        System.out.println("But order total is STILL: $" + String.format("%.2f", order3.getTotal()));
        System.out.println("unitPrice was captured at order time, not from Product.getPrice()");

        System.out.println("\n✓ Phase 2 complete. All patterns demonstrated.");
    }
}
