package com.design.inventory_management_system.src.observer;

import com.design.inventory_management_system.src.model.Product;
import com.design.inventory_management_system.src.model.Warehouse;

/**
 * Observer interface for stock-level events.
 * Implementations can log alerts, send emails, trigger auto-restock, etc.
 */
public interface StockObserver {
    void onLowStock(Product product, Warehouse warehouse, int currentQuantity);
}
