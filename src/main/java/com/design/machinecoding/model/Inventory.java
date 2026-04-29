package com.design.machinecoding.model;

import lombok.Data;

/**
 * Tracks stock for ONE product in ONE warehouse.
 *
 * totalQuantity    = physically present in warehouse
 * reservedQuantity = claimed by pending orders, not yet shipped
 * available        = totalQuantity - reservedQuantity
 */
@Data
public class Inventory {
    private Long productId;
    private Long warehouseId;
    private int totalQuantity;
    private int reservedQuantity;

    public Inventory(Long productId, Long warehouseId, int totalQuantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = 0;
    }

    public synchronized int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public synchronized boolean reserve(int quantity) {
        if (getAvailableQuantity() >= quantity) {
            reservedQuantity += quantity;
            return true;
        }
        return false;
    }

    public synchronized void confirmReservation(int quantity) {
        if (quantity > reservedQuantity) throw new IllegalStateException("Cannot confirm more than reserved");
        reservedQuantity -= quantity;
        totalQuantity -= quantity;
    }

    public synchronized void releaseReservation(int quantity) {
        if (quantity > reservedQuantity) throw new IllegalStateException("Cannot release more than reserved");
        reservedQuantity -= quantity;
    }

    public synchronized void restock(int quantity) {
        totalQuantity += quantity;
    }
}
