package com.design.inventory_management_system.observer;

import com.design.inventory_management_system.model.Product;
import com.design.inventory_management_system.model.Warehouse;

/**
 * Observer interface for stock-level events.
 * Implementations can log alerts, send emails, trigger auto-restock, etc.
 */
public interface StockObserver {
    void onLowStock(Product product, Warehouse warehouse, int currentQuantity);
}
