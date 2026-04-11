package com.design.practice.parkinglot;
import com.design.practice.Entity.ParkingSpot;
import com.design.practice.enums.VehicleType;
import com.design.practice.parkingspotmanager.ParkingSpotManager;

import java.util.Map;

/**
 * [OOP — Composition + Delegation + Map-based Dispatch]
 *
 * ParkingLevel represents a single floor in the parking building.
 * It composes a Map<VehicleType, ParkingSpotManager> — each vehicle type
 * gets its own manager, and the level delegates to the correct one.
 *
 * Key interview points:
 *
 *  1. Map-based dispatch (Map<VehicleType, ParkingSpotManager>):
 *     Instead of if/else or switch on VehicleType, we use a Map lookup.
 *     This is cleaner, O(1), and adding a new VehicleType just means
 *     adding an entry to the map — no code changes in ParkingLevel.
 *     This is a common interview pattern for replacing conditionals
 *     with polymorphism + data structures.
 *
 *  2. Delegation:
 *     ParkingLevel doesn't manage spots itself — it delegates to
 *     ParkingSpotManager. Each class has a single responsibility:
 *       - ParkingLevel: routing to the right manager
 *       - ParkingSpotManager: thread-safe spot allocation
 *
 *  3. Null safety:
 *     hasAvailability() checks manager != null before calling hasFreeSpot().
 *     park() throws if no manager exists — fail-fast with a clear message
 *     is better than returning null and causing a NullPointerException later.
 */
public class ParkingLevel {
    private final int level;
    private Map<VehicleType, ParkingSpotManager> managers ;
    public ParkingLevel(int level, Map<VehicleType, ParkingSpotManager> managers) {
        this.level = level;
        this.managers = managers;
    }
    public boolean hasAvailability(VehicleType type) {
        ParkingSpotManager manager = managers.get(type);
        return manager != null && manager.hasFreeSpot();
    }
    public ParkingSpot park(VehicleType type) {
        ParkingSpotManager manager = managers.get(type);
        if (manager == null) {
            throw new IllegalArgumentException(
                    "No parking manager for vehicle type: " + type);
        }
        return manager.park(type);
    }

    public void unPark(VehicleType type, ParkingSpot spot) {
        ParkingSpotManager manager = managers.get(type);
        if (manager != null) {
            manager.unPark(spot);
        }
    }
    public int getParkingLevelNumber() {
        return level;
    }
}
