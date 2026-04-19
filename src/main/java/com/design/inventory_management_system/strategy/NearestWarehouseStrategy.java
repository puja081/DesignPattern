package com.design.inventory_management_system.strategy;

import com.design.inventory_management_system.model.Inventory;
import com.design.inventory_management_system.model.Product;
import com.design.inventory_management_system.model.Warehouse;

import java.util.List;

/**
 * Picks the first warehouse (by list order) that has sufficient stock.
 * In a real system, warehouses would be sorted by geographic proximity
 * to the customer's delivery address.
 */
public class NearestWarehouseStrategy implements WarehouseSelectionStrategy {

    @Override
    public Warehouse selectWarehouse(Product product, int quantity, List<Warehouse> warehouses) {
        for (Warehouse warehouse : warehouses) {
            Inventory inventory = warehouse.getInventory(product.getId());
            if (inventory != null && inventory.getAvailableQuantity() >= quantity) {
                return warehouse;
            }
        }
        return null;
    }
}
