package model;

public class OrderItem {
    private final Product product;
    private final int quantity;
    private final double unitPrice;
    private final Warehouse fulfilledFrom;

    public OrderItem(Product product, int quantity, Warehouse fulfilledFrom) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.fulfilledFrom = fulfilledFrom;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public Warehouse getFulfilledFrom() { return fulfilledFrom; }

    public double getSubtotal() {
        return unitPrice * quantity;
    }

    @Override
    public String toString() {
        return String.format("OrderItem{product=%s, qty=%d, from=%s, subtotal=%.2f}",
                product.getName(), quantity, fulfilledFrom.getName(), getSubtotal());
    }
}
