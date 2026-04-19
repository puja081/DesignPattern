package com.design.inventory_management_system.observer;

import com.design.inventory_management_system.model.Product;
import com.design.inventory_management_system.model.Warehouse;

/**
 * Automatically restocks a fixed amount when stock drops below threshold.
 * In production, this would place a purchase order to the supplier instead.
 */
public class RestockObserver implements StockObserver {
    private final int restockAmount;

    public RestockObserver(int restockAmount) {
        this.restockAmount = restockAmount;
    }

    @Override
    public void onLowStock(Product product, Warehouse warehouse, int currentQuantity) {
        System.out.printf("  [AUTO-RESTOCK] Adding %d units of '%s' to warehouse '%s'%n",
                restockAmount, product.getName(), warehouse.getName());
        warehouse.addProduct(product, restockAmount);
    }
}
