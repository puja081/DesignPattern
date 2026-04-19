package com.design.inventory_management_system.src.exception;

public class InsufficientStockException extends RuntimeException {
    private final String productId;

    public InsufficientStockException(String productId) {
        super("Insufficient stock for product: " + productId);
        this.productId = productId;
    }

    public String getProductId() { return productId; }
}
