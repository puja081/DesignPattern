package com.design.inventory_management_system.src.strategy;

import com.design.inventory_management_system.src.model.Product;
import com.design.inventory_management_system.src.model.Warehouse;

import java.util.List;

/**
 * Strategy interface for selecting which warehouse fulfills an order item.
 * New strategies can be added without modifying InventoryService (Open/Closed Principle).
 */
public interface WarehouseSelectionStrategy {
    /**
     * @return the best warehouse to fulfill this order, or null if no warehouse has sufficient stock
     */
    Warehouse selectWarehouse(Product product, int quantity, List<Warehouse> warehouses);
}
