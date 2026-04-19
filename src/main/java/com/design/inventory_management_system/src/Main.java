package com.design.inventory_management_system.src;

import com.design.inventory_management_system.src.model.*;
import com.design.inventory_management_system.src.observer.LowStockAlertObserver;
import com.design.inventory_management_system.src.observer.RestockObserver;
import com.design.inventory_management_system.src.service.InventoryService;
import com.design.inventory_management_system.src.strategy.MaxStockWarehouseStrategy;
import com.design.inventory_management_system.src.strategy.NearestWarehouseStrategy;
import com.design.inventory_management_system.src.strategy.WarehouseSelectionStrategy;

import com.design.inventory_management_system.src.service.OrderService;

import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Inventory Management System — Demo");
        System.out.println("========================================\n");

        // ─── 1. Bootstrap: Strategy + Services + Observers ─────────
        WarehouseSelectionStrategy strategy = new NearestWarehouseStrategy();
        InventoryService inventoryService = new InventoryService(strategy, 10);
        OrderService orderService = new OrderService(inventoryService);

        inventoryService.registerObserver(new LowStockAlertObserver());
        inventoryService.registerObserver(new RestockObserver(50));

        // ─── 2. Create Users ──────────────────────────────────────
        User admin    = new User("U001", "Ravi (Admin)",    "ravi@company.com",   UserRole.ADMIN);
        User customer = new User("U002", "Priya (Customer)","priya@gmail.com",    UserRole.CUSTOMER);
        User customer2 = new User("U003", "Amit (Customer)", "amit@gmail.com",    UserRole.CUSTOMER);
        System.out.println("Users: " + admin + ", " + customer + ", " + customer2);

        // ─── 3. Admin: Create Products ────────────────────────────
        Product laptop = new Product("P001", "MacBook Pro", Category.ELECTRONICS, 2499.99, "16-inch M3 Max");
        Product phone  = new Product("P002", "iPhone 15",   Category.ELECTRONICS, 999.99,  "Pro Max 256GB");
        Product shirt  = new Product("P003", "Polo Shirt",  Category.CLOTHING,    49.99,   "Cotton, Blue, L");

        inventoryService.addProduct(laptop);
        inventoryService.addProduct(phone);
        inventoryService.addProduct(shirt);

        // ─── 4. Admin: Create Warehouses ──────────────────────────
        Warehouse whBangalore = new Warehouse("WH01", "Bangalore Hub", "Whitefield, Bangalore");
        Warehouse whMumbai    = new Warehouse("WH02", "Mumbai Hub",    "Andheri, Mumbai");

        inventoryService.addWarehouse(whBangalore);
        inventoryService.addWarehouse(whMumbai);

        // ─── 5. Admin: Stock Warehouses ───────────────────────────
        inventoryService.addStock("WH01", "P001", 15);
        inventoryService.addStock("WH01", "P002", 100);
        inventoryService.addStock("WH01", "P003", 200);
        inventoryService.addStock("WH02", "P001", 50);
        inventoryService.addStock("WH02", "P002", 80);

        System.out.println("\nInitial stock loaded:");
        printWarehouseStock(whBangalore);
        printWarehouseStock(whMumbai);

        // ─── 6. Customer: Place Order (Happy Path) ────────────────
        System.out.println("\n>>> Priya places order: 2 laptops + 3 phones");
        Map<String, Integer> order1Items = new LinkedHashMap<>();
        order1Items.put("P001", 2);
        order1Items.put("P002", 3);

        Order order1 = orderService.placeOrder(customer, order1Items);
        System.out.println("  " + order1);
        for (OrderItem item : order1.getItems()) {
            System.out.println("    " + item);
        }
        System.out.println("  Stock after reservation:");
        printWarehouseStock(whBangalore);

        // ─── 7. Confirm Order (payment successful) ────────────────
        System.out.println("\n>>> Confirm Order 1 (payment received)");
        orderService.confirmOrder(order1.getId());
        System.out.println("  " + order1);
        System.out.println("  Stock after confirmation (permanently deducted):");
        printWarehouseStock(whBangalore);

        // ─── 8. Order triggers LOW STOCK alert + auto-restock ─────
        System.out.println("\n>>> Amit orders 8 laptops (triggers low stock!)");
        Map<String, Integer> order2Items = new LinkedHashMap<>();
        order2Items.put("P001", 8);

        Order order2 = orderService.placeOrder(customer2, order2Items);
        System.out.println("  " + order2);
        System.out.println("  Stock after (notice auto-restock kicked in):");
        printWarehouseStock(whBangalore);

        // ─── 9. Cancel Order (stock released back) ────────────────
        System.out.println("\n>>> Amit cancels order");
        orderService.cancelOrder(order2.getId());
        System.out.println("  " + order2);
        System.out.println("  Stock after cancellation (reserved released):");
        printWarehouseStock(whBangalore);

        // ─── 10. Switch Strategy at Runtime ───────────────────────
        System.out.println("\n>>> Switch to MaxStockWarehouseStrategy");
        inventoryService.setSelectionStrategy(new MaxStockWarehouseStrategy());

        Map<String, Integer> order3Items = new LinkedHashMap<>();
        order3Items.put("P001", 3);

        Order order3 = orderService.placeOrder(customer, order3Items);
        System.out.println("  " + order3);
        for (OrderItem item : order3.getItems()) {
            System.out.println("    " + item);
        }

        // ─── 11. Role Enforcement: Admin tries to place order ─────
        System.out.println("\n>>> Admin tries to place order (should fail)");
        try {
            Map<String, Integer> badOrder = new LinkedHashMap<>();
            badOrder.put("P002", 1);
            orderService.placeOrder(admin, badOrder);
        } catch (IllegalArgumentException e) {
            System.out.println("  Blocked: " + e.getMessage());
        }

        // ─── 12. Insufficient Stock: Saga Rollback Demo ──────────
        System.out.println("\n>>> Priya orders 5 phones + 999 laptops (second item will fail)");
        try {
            Map<String, Integer> order4Items = new LinkedHashMap<>();
            order4Items.put("P002", 5);
            order4Items.put("P001", 999);
            orderService.placeOrder(customer, order4Items);
        } catch (Exception e) {
            System.out.println("  Expected failure: " + e.getMessage());
            System.out.println("  Phone stock unchanged (saga rollback worked):");
            printWarehouseStock(whMumbai);
        }

        System.out.println("\n========================================");
        System.out.println("  Demo Complete");
        System.out.println("========================================");
    }

    private static void printWarehouseStock(Warehouse warehouse) {
        System.out.println("    [" + warehouse.getName() + "]");
        for (Inventory inv : warehouse.getAllInventory()) {
            System.out.println("      " + inv);
        }
    }
}
