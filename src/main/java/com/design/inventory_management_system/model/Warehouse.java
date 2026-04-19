package com.design.inventory_management_system.model;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Warehouse {
    private final String id;
    private final String name;
    private final String address;
    private final Map<String, Inventory> inventoryMap; // productId -> Inventory

    public Warehouse(String id, String name, String address) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.inventoryMap = new ConcurrentHashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }

    /**
     * Adds stock for a product. If product doesn't exist in this warehouse,
     * creates a new Inventory entry. Uses putIfAbsent for thread safety
     * on the map operation; restock() is synchronized internally.
     */
    public void addProduct(Product product, int quantity) {
        inventoryMap.putIfAbsent(product.getId(), new Inventory(product, 0));
        inventoryMap.get(product.getId()).restock(quantity);
    }

    public Inventory getInventory(String productId) {
        return inventoryMap.get(productId);
    }

    public boolean hasStock(String productId, int requiredQuantity) {
        Inventory inv = inventoryMap.get(productId);
        return inv != null && inv.getAvailableQuantity() >= requiredQuantity;
    }

    public Collection<Inventory> getAllInventory() {
        return inventoryMap.values();
    }

    @Override
    public String toString() {
        return String.format("Warehouse{id='%s', name='%s', address='%s', products=%d}",
                id, name, address, inventoryMap.size());
    }
}
