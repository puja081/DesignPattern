package strategy;

import model.Inventory;
import model.Product;
import model.Warehouse;

import java.util.List;

/**
 * Picks the warehouse with the highest available stock for the product.
 * Useful for load balancing — avoids draining a single warehouse.
 */
public class MaxStockWarehouseStrategy implements WarehouseSelectionStrategy {

    @Override
    public Warehouse selectWarehouse(Product product, int quantity, List<Warehouse> warehouses) {
        Warehouse best = null;
        int maxAvailable = 0;

        for (Warehouse warehouse : warehouses) {
            Inventory inventory = warehouse.getInventory(product.getId());
            if (inventory != null && inventory.getAvailableQuantity() >= quantity) {
                if (inventory.getAvailableQuantity() > maxAvailable) {
                    maxAvailable = inventory.getAvailableQuantity();
                    best = warehouse;
                }
            }
        }
        return best;
    }
}
