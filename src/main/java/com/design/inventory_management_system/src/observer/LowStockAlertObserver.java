package observer;

import model.Product;
import model.Warehouse;

public class LowStockAlertObserver implements StockObserver {

    @Override
    public void onLowStock(Product product, Warehouse warehouse, int currentQuantity) {
        System.out.printf("  [ALERT] Low stock: '%s' in warehouse '%s' — only %d left%n",
                product.getName(), warehouse.getName(), currentQuantity);
    }
}
