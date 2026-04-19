package com.design.inventory_management_system.service;

import com.design.inventory_management_system.model.Inventory;
import com.design.inventory_management_system.model.Product;
import com.design.inventory_management_system.model.Warehouse;
import com.design.inventory_management_system.observer.StockObserver;
import com.design.inventory_management_system.strategy.WarehouseSelectionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryService {
    private final Map<String, Warehouse> warehouses;
    private final Map<String, Product> products;
    private final List<StockObserver> observers;
    private WarehouseSelectionStrategy selectionStrategy;
    private int lowStockThreshold;

    public InventoryService(WarehouseSelectionStrategy selectionStrategy, int lowStockThreshold) {
        this.warehouses = new ConcurrentHashMap<>();
        this.products = new ConcurrentHashMap<>();
        this.observers = new ArrayList<>();
        this.selectionStrategy = selectionStrategy;
        this.lowStockThreshold = lowStockThreshold;
    }

    // ── Warehouse & Product Registration ─────────────────────

    public void addWarehouse(Warehouse warehouse) {
        warehouses.put(warehouse.getId(), warehouse);
    }

    public void addProduct(Product product) {
        products.put(product.getId(), product);
    }

    public Product getProduct(String productId) {
        return products.get(productId);
    }

    public Warehouse getWarehouse(String warehouseId) {
        return warehouses.get(warehouseId);
    }

    // ── Stock Operations ─────────────────────────────────────

    public void addStock(String warehouseId, String productId, int quantity) {
        Warehouse warehouse = warehouses.get(warehouseId);
        Product product = products.get(productId);
        if (warehouse == null) throw new IllegalArgumentException("Warehouse not found: " + warehouseId);
        if (product == null) throw new IllegalArgumentException("Product not found: " + productId);
        warehouse.addProduct(product, quantity);
    }

    /**
     * Reserves stock for a product using the configured warehouse selection strategy.
     * Returns the warehouse that fulfilled the reservation, or null if no warehouse has stock.
     *
     * Thread-safety: The strategy's selectWarehouse checks availability, and then
     * Inventory.reserve() does an atomic check-and-set inside synchronized.
     * If another thread reserved between selection and reserve(), reserve() returns false
     * and we fall through to return null. In production, you'd retry with next-best warehouse.
     */
    public Warehouse reserveStock(String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) throw new IllegalArgumentException("Product not found: " + productId);

        Warehouse selected = selectionStrategy.selectWarehouse(
                product, quantity, new ArrayList<>(warehouses.values()));

        if (selected == null) return null;

        Inventory inventory = selected.getInventory(productId);
        boolean reserved = inventory.reserve(quantity);

        if (!reserved) return null; // lost race to another thread

        if (inventory.getAvailableQuantity() < lowStockThreshold) {
            notifyObservers(product, selected, inventory.getAvailableQuantity());
        }

        return selected;
    }

    /**
     * Confirms a reservation — permanently deducts stock (called after payment).
     */
    public void confirmStock(String warehouseId, String productId, int quantity) {
        Warehouse warehouse = warehouses.get(warehouseId);
        if (warehouse == null) throw new IllegalArgumentException("Warehouse not found: " + warehouseId);
        Inventory inventory = warehouse.getInventory(productId);
        if (inventory == null) throw new IllegalArgumentException("Product not in warehouse");
        inventory.confirmReservation(quantity);
    }

    /**
     * Releases a reservation — returns reserved stock to available pool (called on cancellation).
     */
    public void releaseStock(String warehouseId, String productId, int quantity) {
        Warehouse warehouse = warehouses.get(warehouseId);
        if (warehouse == null) return;
        Inventory inventory = warehouse.getInventory(productId);
        if (inventory == null) return;
        inventory.releaseReservation(quantity);
    }

    // ── Strategy (runtime swappable) ─────────────────────────

    public void setSelectionStrategy(WarehouseSelectionStrategy strategy) {
        this.selectionStrategy = strategy;
    }

    public void setLowStockThreshold(int threshold) {
        this.lowStockThreshold = threshold;
    }

    // ── Observer Management ──────────────────────────────────

    public void registerObserver(StockObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(StockObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(Product product, Warehouse warehouse, int currentQuantity) {
        for (StockObserver observer : observers) {
            observer.onLowStock(product, warehouse, currentQuantity);
        }
    }
}
