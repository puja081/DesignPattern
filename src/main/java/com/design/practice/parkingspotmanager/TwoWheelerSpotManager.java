package com.design.practice.parkingspotmanager;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.enums.VehicleType;

import java.util.List;

public class TwoWheelerSpotManager extends ParkingSpotManager{
    public TwoWheelerSpotManager(List<ParkingSpot> spots, ParkingSpotLookupStrategy strategy){
        super(strategy, spots);
    }
}
