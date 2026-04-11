package com.design.practice.parkingspotmanager;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.enums.VehicleType;
import com.design.practice.parkinglot.ParkingLevel;
import com.design.practice.parkinglot.ParkingLot;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ParkingSpotManager {
    private final ParkingSpotLookupStrategy strategy;
    private final ReentrantLock reentrantLock = new ReentrantLock(true);
    private final List<ParkingSpot> parkingSpotList;
    public ParkingSpotManager(ParkingSpotLookupStrategy strategy, List<ParkingSpot> parkingSpotList ) {
        this.strategy = strategy;
        this.parkingSpotList = parkingSpotList;
    }
    public ParkingSpot park(VehicleType type){
        reentrantLock.lock();
        try {
            ParkingSpot spot = strategy.selectSpot(parkingSpotList);
            if (spot == null) {
                return null;
            }
            spot.occupySpot();
            return spot;
        } finally {
            reentrantLock.unlock();
        }
    }
    public void unPark(ParkingSpot spot){
        reentrantLock.lock();
        try {
            spot.releaseSpot();
        } finally {
            reentrantLock.unlock();
        }
    }
    public boolean hasFreeSpot() {
        reentrantLock.lock();
        try {
            return parkingSpotList.stream().anyMatch(ParkingSpot::isSpotFree);
        } finally {
            reentrantLock.unlock();
        }

    }
}
