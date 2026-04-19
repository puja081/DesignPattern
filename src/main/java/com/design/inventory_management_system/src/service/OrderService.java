package com.design.inventory_management_system.src.service;

import com.design.inventory_management_system.src.exception.InsufficientStockException;
import com.design.inventory_management_system.src.model.Order;
import com.design.inventory_management_system.src.model.User;
import com.design.inventory_management_system.src.exception.OrderNotFoundException;
import com.design.inventory_management_system.src.model.OrderItem;
import com.design.inventory_management_system.src.model.OrderStatus;
import com.design.inventory_management_system.src.model.Product;
import com.design.inventory_management_system.src.model.Warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {
    private final InventoryService inventoryService;
    private final Map<String, Order> orders;
    private final AtomicInteger orderCounter;

    public OrderService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
        this.orders = new LinkedHashMap<>();
        this.orderCounter = new AtomicInteger(0);
    }

    /**
     * Places an order for the given products and quantities.
     *
     * Flow:
     *   1. Validate user has CUSTOMER role
     *   2. For each item, reserve stock via InventoryService
     *   3. If any reservation fails, rollback ALL previous reservations (saga pattern)
     *   4. If all succeed, create Order with status PENDING
     *
     * @param customer the user placing the order
     * @param productQuantities map of productId -> quantity
     * @return the created Order
     * @throws InsufficientStockException if any product can't be fulfilled
     * @throws IllegalArgumentException if user doesn't have permission
     */
    public Order placeOrder(User customer, Map<String, Integer> productQuantities) {
        if (!customer.canPlaceOrder()) {
            throw new IllegalArgumentException("User " + customer.getName() + " (role=" + customer.getRole() + ") cannot place orders");
        }

        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            for (Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
                String productId = entry.getKey();
                int quantity = entry.getValue();

                Warehouse warehouse = inventoryService.reserveStock(productId, quantity);
                if (warehouse == null) {
                    throw new InsufficientStockException(productId);
                }

                Product product = inventoryService.getProduct(productId);
                reservedItems.add(new OrderItem(product, quantity, warehouse));
            }
        } catch (InsufficientStockException e) {
            for (OrderItem item : reservedItems) {
                inventoryService.releaseStock(
                        item.getFulfilledFrom().getId(),
                        item.getProduct().getId(),
                        item.getQuantity()
                );
            }
            throw e;
        }

        String orderId = "ORD-" + orderCounter.incrementAndGet();
        Order order = new Order(orderId, customer, reservedItems);
        orders.put(orderId, order);
        return order;
    }

    /**
     * Confirms an order — permanently deducts reserved stock.
     * Called after successful payment.
     */
    public void confirmOrder(String orderId) {
        Order order = getOrderOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed. Current: " + order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmStock(
                    item.getFulfilledFrom().getId(),
                    item.getProduct().getId(),
                    item.getQuantity()
            );
        }
        order.setStatus(OrderStatus.CONFIRMED);
    }

    /**
     * Cancels an order — releases all reserved stock back to available pool.
     */
    public void cancelOrder(String orderId) {
        Order order = getOrderOrThrow(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be cancelled. Current: " + order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(
                    item.getFulfilledFrom().getId(),
                    item.getProduct().getId(),
                    item.getQuantity()
            );
        }
        order.setStatus(OrderStatus.CANCELLED);
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    private Order getOrderOrThrow(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new OrderNotFoundException(orderId);
        return order;
    }
}
