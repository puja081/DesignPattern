package com.design.practice.parkinglot;
import com.design.practice.Entity.ParkingSpot;
import com.design.practice.enums.VehicleType;
import com.design.practice.parkingspotmanager.ParkingSpotManager;

import java.util.Map;

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
