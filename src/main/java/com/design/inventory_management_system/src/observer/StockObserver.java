package observer;

import model.Product;
import model.Warehouse;

/**
 * Observer interface for stock-level events.
 * Implementations can log alerts, send emails, trigger auto-restock, etc.
 */
public interface StockObserver {
    void onLowStock(Product product, Warehouse warehouse, int currentQuantity);
}
