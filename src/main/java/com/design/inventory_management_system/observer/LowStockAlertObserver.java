package com.design.inventory_management_system.observer;

import com.design.inventory_management_system.model.Product;
import com.design.inventory_management_system.model.Warehouse;

public class LowStockAlertObserver implements StockObserver {

    @Override
    public void onLowStock(Product product, Warehouse warehouse, int currentQuantity) {
        System.out.printf("  [ALERT] Low stock: '%s' in warehouse '%s' — only %d left%n",
                product.getName(), warehouse.getName(), currentQuantity);
    }
}
