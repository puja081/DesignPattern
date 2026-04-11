package com.design.practice.factory;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.Entity.Vehicle;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.enums.VehicleType;
import com.design.practice.parkingspotmanager.FourWheelerSpotManager;
import com.design.practice.parkingspotmanager.ParkingSpotManager;
import com.design.practice.parkingspotmanager.TwoWheelerSpotManager;

import java.util.List;

public class ParkingSpotManagerFactory {
    private final ParkingSpotLookupStrategy parkingSpotLookupStrategy;

    public ParkingSpotManagerFactory (ParkingSpotLookupStrategy parkingSpotLookupStrategy) {
         this.parkingSpotLookupStrategy = parkingSpotLookupStrategy;
    }

    public ParkingSpotManager create(VehicleType vehicleType, List<ParkingSpot> spots) {
        switch (vehicleType){
            case TWO_WHEELER -> {
                return new TwoWheelerSpotManager(spots, parkingSpotLookupStrategy);
            }
            case FOUR_WHEELER -> {
                return new FourWheelerSpotManager(spots, parkingSpotLookupStrategy);
            }
            default -> {
                throw new IllegalArgumentException("Unknown vehicle type: " + vehicleType);
            }
        }
    }
}
