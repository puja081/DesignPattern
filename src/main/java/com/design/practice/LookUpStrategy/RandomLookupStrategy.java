package com.design.practice.LookUpStrategy;

import com.design.practice.Entity.ParkingSpot;

import java.util.List;

public class RandomLookupStrategy implements ParkingSpotLookupStrategy{
    public ParkingSpot selectSpot(List<ParkingSpot> spots) {
        for(ParkingSpot sp : spots) {
            if(sp.isSpotFree()){
                return sp;
            }
        }
        return null;
    }
}
