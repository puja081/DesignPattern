package com.design.practice.parking_lot.LookUpStrategy;

import com.design.practice.parking_lot.Entity.ParkingSpot;

import java.util.List;

/**
 * [Strategy Pattern — Concrete Strategy]
 *
 * Implements the "first available" spot selection algorithm.
 * (Named Random but actually returns the first free spot — in a real
 *  system you might shuffle or use Random to pick among free spots.)
 *
 * Key interview points:
 *  - Each concrete strategy is a single-responsibility class: it only
 *    knows HOW to select a spot, not WHEN or WHERE it's used.
 *  - Returning null when no spot is free pushes the "parking full"
 *    decision up to the caller (ParkingSpotManager). This separation
 *    keeps the strategy focused.
 *  - Alternative strategies could be: NearestToElevator, HighestFloorFirst,
 *    RandomShuffle, etc. — all plug in without changing ParkingSpotManager.
 */
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
