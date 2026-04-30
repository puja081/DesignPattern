package com.design.machinecoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class OrderItem {
    private Long productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private Long fulfilledFromWarehouseId;

    public double getSubtotal() {
        return unitPrice * quantity;
    }
}
