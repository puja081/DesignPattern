package com.design.inventory_management_system.model;

/**
 * Tracks stock for ONE product in ONE warehouse.
 * All mutating methods are synchronized to prevent over-selling
 * under concurrent order placement.
 *
 * Quantities:
 *   totalQuantity    = physically present in warehouse
 *   reservedQuantity = claimed by pending orders, not yet shipped
 *   available        = totalQuantity - reservedQuantity
 */
public class Inventory {
    private final Product product;
    private int totalQuantity;
    private int reservedQuantity;

    public Inventory(Product product, int initialQuantity) {
        if (initialQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        this.product = product;
        this.totalQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public Product getProduct() { return product; }

    public synchronized int getTotalQuantity() { return totalQuantity; }

    public synchronized int getReservedQuantity() { return reservedQuantity; }

    public synchronized int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    /**
     * Attempt to reserve `quantity` units. Returns true if successful.
     * This is the hot path for concurrency — synchronized ensures
     * two threads can't both see "available = 5" and each reserve 5.
     */
    public synchronized boolean reserve(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Reserve quantity must be positive");
        if (getAvailableQuantity() >= quantity) {
            reservedQuantity += quantity;
            return true;
        }
        return false;
    }

    /**
     * Called after payment/shipping confirmation.
     * Converts reserved stock into "consumed" — both counters decrease.
     */
    public synchronized void confirmReservation(int quantity) {
        if (quantity > reservedQuantity) throw new IllegalStateException("Cannot confirm more than reserved");
        reservedQuantity -= quantity;
        totalQuantity -= quantity;
    }

    /**
     * Called on order cancellation — returns reserved stock to available pool.
     */
    public synchronized void releaseReservation(int quantity) {
        if (quantity > reservedQuantity) throw new IllegalStateException("Cannot release more than reserved");
        reservedQuantity -= quantity;
    }

    /**
     * Admin operation — adds new stock to the warehouse.
     */
    public synchronized void restock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Restock quantity must be positive");
        totalQuantity += quantity;
    }

    @Override
    public String toString() {
        return String.format("Inventory{product=%s, total=%d, reserved=%d, available=%d}",
                product.getName(), totalQuantity, reservedQuantity, getAvailableQuantity());
    }
}
