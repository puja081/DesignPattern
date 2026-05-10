package com.design.machinecoding.model;

import com.design.machinecoding.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class Order {
    private Long id;
    private String customerName;
    private List<OrderItem> items;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public double getTotalAmount() {
        return items.stream().mapToDouble(OrderItem::getSubtotal).sum();
    }
}
