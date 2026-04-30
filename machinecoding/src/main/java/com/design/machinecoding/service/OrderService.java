package com.design.machinecoding.service;

import com.design.machinecoding.dto.OrderItemRequest;
import com.design.machinecoding.dto.PlaceOrderRequest;
import com.design.machinecoding.enums.OrderStatus;
import com.design.machinecoding.exception.BadRequestException;
import com.design.machinecoding.exception.ResourceNotFoundException;
import com.design.machinecoding.model.Order;
import com.design.machinecoding.model.OrderItem;
import com.design.machinecoding.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final InventoryService inventoryService;

    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    /**
     * Places an order with saga-style rollback.
     * For each item, reserves stock. If any reservation fails,
     * all previous reservations are rolled back.
     */
    public Order placeOrder(PlaceOrderRequest req) {
        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            for (OrderItemRequest itemReq : req.items()) {
                Product product = productService.getById(itemReq.productId());

                Long warehouseId = inventoryService.reserveStock(itemReq.productId(), itemReq.quantity());
                if (warehouseId == null) {
                    throw new BadRequestException(
                            "Insufficient stock for product: " + product.getName() + " (id=" + itemReq.productId() + ")");
                }

                reservedItems.add(OrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .quantity(itemReq.quantity())
                        .unitPrice(product.getPrice())
                        .fulfilledFromWarehouseId(warehouseId)
                        .build());
            }
        } catch (BadRequestException e) {
            for (OrderItem item : reservedItems) {
                inventoryService.releaseStock(
                        item.getFulfilledFromWarehouseId(),
                        item.getProductId(),
                        item.getQuantity());
            }
            throw e;
        }

        Order order = Order.builder()
                .id(idGen.getAndIncrement())
                .customerName(req.customerName())
                .items(reservedItems)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        store.put(order.getId(), order);
        return order;
    }

    public Order getById(Long id) {
        Order order = store.get(id);
        if (order == null) throw new ResourceNotFoundException("Order", id);
        return order;
    }

    public Collection<Order> getAll() {
        return store.values();
    }

    public Order confirmOrder(Long id) {
        Order order = getById(id);
        validateTransition(order, OrderStatus.PENDING, "confirm");

        for (OrderItem item : order.getItems()) {
            inventoryService.confirmStock(
                    item.getFulfilledFromWarehouseId(),
                    item.getProductId(),
                    item.getQuantity());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }

    public Order cancelOrder(Long id) {
        Order order = getById(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order is already cancelled");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING orders can be cancelled. Current: " + order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseStock(
                    item.getFulfilledFromWarehouseId(),
                    item.getProductId(),
                    item.getQuantity());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return order;
    }

    public Order shipOrder(Long id) {
        Order order = getById(id);
        validateTransition(order, OrderStatus.CONFIRMED, "ship");
        order.setStatus(OrderStatus.SHIPPED);
        return order;
    }

    public Order deliverOrder(Long id) {
        Order order = getById(id);
        validateTransition(order, OrderStatus.SHIPPED, "deliver");
        order.setStatus(OrderStatus.DELIVERED);
        return order;
    }

    private void validateTransition(Order order, OrderStatus requiredStatus, String action) {
        if (order.getStatus() != requiredStatus) {
            throw new BadRequestException(
                    "Cannot " + action + " order. Expected status: " + requiredStatus + ", actual: " + order.getStatus());
        }
    }
}
