package com.design.practice.Entity;

/**
 * [OOP — Encapsulation + State Management]
 *
 * ParkingSpot encapsulates the availability state of a single spot.
 * External code never touches 'isFree' directly — it must go through
 * occupySpot() / releaseSpot(), which lets us add validation, logging,
 * or event-firing later without changing callers (Open/Closed Principle).
 *
 * Key interview points:
 *  - 'private' fields + public methods = true encapsulation.
 *  - Default field value (isFree = true) means every new spot starts available,
 *    reducing the risk of forgetting to initialize.
 *  - This class is NOT thread-safe on its own — thread safety is handled at a
 *    higher level by ParkingSpotManager using ReentrantLock. This is a valid
 *    design choice: push synchronization to the layer that owns the collection.
 */
public class ParkingSpot {

    private String spotId;
    private boolean isFree = true;
    public ParkingSpot(String spotId) {
        this.spotId = spotId;
    }

    public boolean isSpotFree() {
        return isFree;
    }
    public void occupySpot() {
        isFree = false;
    }
    public void releaseSpot() {
        isFree = true;
    }

    public String getSpotId() {
        return spotId;
    }
}
