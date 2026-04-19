package model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class Order {
    private final String id;
    private final User customer;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime createdAt;

    public Order(String id, User customer, List<OrderItem> items) {
        this.id = id;
        this.customer = customer;
        this.items = items;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public User getCustomer() { return customer; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setStatus(OrderStatus status) { this.status = status; }

    public double getTotalAmount() {
        return items.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();
    }

    @Override
    public String toString() {
        return String.format("Order{id='%s', customer='%s', status=%s, items=%d, total=%.2f, created=%s}",
                id, customer.getName(), status, items.size(), getTotalAmount(), createdAt);
    }
}
