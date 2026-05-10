package com.design.inventory_management_system.src.model;

public enum OrderStatus {
    PENDING,      // stock reserved, awaiting confirmation/payment
    CONFIRMED,    // payment received, ready for fulfillment
    SHIPPED,
    DELIVERED,
    CANCELLED     // stock released back to available pool
}
