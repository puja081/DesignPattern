package com.design.practice.parkingspotmanager;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.enums.VehicleType;

import java.util.List;

/**
 * [OOP — Inheritance: extending a base class]
 *
 * TwoWheelerSpotManager extends ParkingSpotManager. Currently it adds no
 * extra behavior — it exists to give the Factory a distinct type to create
 * and to allow future customization (e.g., two-wheelers might have smaller
 * spots, different pricing hooks, or max-occupancy rules).
 *
 * Key interview points:
 *  - super(strategy, spots) calls the parent constructor. In Java,
 *    constructors are NOT inherited — subclasses must explicitly call super().
 *    If you don't, the compiler inserts super() (no-arg) which would fail
 *    here since ParkingSpotManager has no no-arg constructor.
 *  - Even though this class is "empty", it serves a purpose in the Factory
 *    pattern: the factory returns the right type based on VehicleType.
 *    If behavior diverges later, the change is localized to this class.
 *  - This is a good example of "plan for extension" — even if today the
 *    subclass is trivial, the class hierarchy is ready for specialization.
 */
public class TwoWheelerSpotManager extends ParkingSpotManager{
    public TwoWheelerSpotManager(List<ParkingSpot> spots, ParkingSpotLookupStrategy strategy){
        super(strategy, spots);
    }
}
