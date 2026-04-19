package com.design.inventory_management_system;

import com.design.inventory_management_system.observer.LowStockAlertObserver;
import com.design.inventory_management_system.observer.RestockObserver;
import com.design.inventory_management_system.service.InventoryService;
import com.design.inventory_management_system.service.OrderService;
import com.design.inventory_management_system.strategy.MaxStockWarehouseStrategy;
import com.design.inventory_management_system.strategy.NearestWarehouseStrategy;
import com.design.inventory_management_system.strategy.WarehouseSelectionStrategy;
import model.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Driver class demonstrating the full Inventory Management System.
 * Run this to see all features in action.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("=== Inventory Management System Demo ===\n");

        // ── 1. Setup: Strategy + Service Layer ───────────────────
        WarehouseSelectionStrategy strategy = new NearestWarehouseStrategy();
        InventoryService inventoryService = new InventoryService(strategy, /* lowStockThreshold */ 10);
        OrderService orderService = new OrderService(inventoryService);

        // Register observers for low-stock events
        inventoryService.registerObserver(new LowStockAlertObserver());
        inventoryService.registerObserver(new RestockObserver(50));

        // ── 2. Create Products ───────────────────────────────────
        Product laptop = new Product("P001", "MacBook Pro", Category.ELECTRONICS, 2499.99, "16-inch M3 Max");
        Product phone  = new Product("P002", "iPhone 15",   Category.ELECTRONICS, 999.99,  "Pro Max 256GB");
        Product shirt  = new Product("P003", "Polo Shirt",  Category.CLOTHING,    49.99,   "Cotton, Blue, L");

        inventoryService.addProduct(laptop);
        inventoryService.addProduct(phone);
        inventoryService.addProduct(shirt);
        System.out.println("Products registered: " + laptop + ", " + phone + ", " + shirt);

        // ── 3. Create Warehouses ─────────────────────────────────
        Warehouse whBangalore = new Warehouse("WH01", "Bangalore Hub", "Whitefield, Bangalore");
        Warehouse whMumbai    = new Warehouse("WH02", "Mumbai Hub",    "Andheri, Mumbai");

        inventoryService.addWarehouse(whBangalore);
        inventoryService.addWarehouse(whMumbai);
        System.out.println("Warehouses registered: " + whBangalore + ", " + whMumbai);

        // ── 4. Add Stock ─────────────────────────────────────────
        inventoryService.addStock("WH01", "P001", 15);   // 15 laptops in Bangalore
        inventoryService.addStock("WH01", "P002", 100);  // 100 phones in Bangalore
        inventoryService.addStock("WH01", "P003", 200);  // 200 shirts in Bangalore
        inventoryService.addStock("WH02", "P001", 50);   // 50 laptops in Mumbai
        inventoryService.addStock("WH02", "P002", 80);   // 80 phones in Mumbai

        System.out.println("\nStock loaded.");
        printWarehouseStock(whBangalore);
        printWarehouseStock(whMumbai);

        // ── 5. Place Order (Happy Path) ──────────────────────────
        System.out.println("\n--- Placing Order 1: 2 laptops + 3 phones ---");
        Map<String, Integer> order1Items = new LinkedHashMap<>();
        order1Items.put("P001", 2);
        order1Items.put("P002", 3);

        Order order1 = orderService.placeOrder(order1Items);
        System.out.println("Order placed: " + order1);
        for (OrderItem item : order1.getItems()) {
            System.out.println("  " + item);
        }
        printWarehouseStock(whBangalore);

        // ── 6. Confirm Order (stock permanently deducted) ────────
        System.out.println("\n--- Confirming Order 1 ---");
        orderService.confirmOrder(order1.getId());
        System.out.println("Order status: " + order1.getStatus());
        printWarehouseStock(whBangalore);

        // ── 7. Place Order that triggers LOW STOCK alert ─────────
        System.out.println("\n--- Placing Order 2: 8 laptops (will trigger low stock!) ---");
        Map<String, Integer> order2Items = new LinkedHashMap<>();
        order2Items.put("P001", 8);

        Order order2 = orderService.placeOrder(order2Items);
        System.out.println("Order placed: " + order2);
        printWarehouseStock(whBangalore);

        // ── 8. Cancel Order (stock released) ─────────────────────
        System.out.println("\n--- Cancelling Order 2 ---");
        orderService.cancelOrder(order2.getId());
        System.out.println("Order status: " + order2.getStatus());
        printWarehouseStock(whBangalore);

        // ── 9. Switch Strategy at Runtime ────────────────────────
        System.out.println("\n--- Switching to MaxStockWarehouseStrategy ---");
        inventoryService.setSelectionStrategy(new MaxStockWarehouseStrategy());

        Map<String, Integer> order3Items = new LinkedHashMap<>();
        order3Items.put("P001", 3);

        Order order3 = orderService.placeOrder(order3Items);
        System.out.println("Order placed with MaxStock strategy: " + order3);
        for (OrderItem item : order3.getItems()) {
            System.out.println("  " + item);
        }

        // ── 10. Insufficient Stock (Rollback Demo) ──────────────
        System.out.println("\n--- Placing Order 4: 999 laptops (will fail + rollback) ---");
        try {
            Map<String, Integer> order4Items = new LinkedHashMap<>();
            order4Items.put("P002", 5);    // this will succeed
            order4Items.put("P001", 999);  // this will fail → triggers rollback of P002
            orderService.placeOrder(order4Items);
        } catch (Exception e) {
            System.out.println("Expected failure: " + e.getMessage());
            System.out.println("Phone stock unchanged (rollback worked):");
            printWarehouseStock(whMumbai);
        }

        System.out.println("\n=== Demo Complete ===");
    }

    private static void printWarehouseStock(Warehouse warehouse) {
        System.out.println("  [" + warehouse.getName() + "] stock:");
        for (Inventory inv : warehouse.getAllInventory()) {
            System.out.println("    " + inv);
        }
    }
}
