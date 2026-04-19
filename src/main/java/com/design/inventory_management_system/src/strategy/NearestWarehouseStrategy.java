package strategy;

import model.Inventory;
import model.Product;
import model.Warehouse;

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
