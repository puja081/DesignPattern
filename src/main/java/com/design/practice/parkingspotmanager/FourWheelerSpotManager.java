package com.design.practice.parkingspotmanager;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;

import java.util.List;

public class FourWheelerSpotManager extends ParkingSpotManager{
    public FourWheelerSpotManager(List<ParkingSpot> spots, ParkingSpotLookupStrategy strategy){
        super(strategy, spots);
    }
}
