package com.design.practice.parkingspotmanager;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;

import java.util.List;

/**
 * [OOP — Inheritance: extending a base class]
 *
 * FourWheelerSpotManager extends ParkingSpotManager. Like TwoWheelerSpotManager,
 * it currently adds no extra behavior but provides a distinct type for the
 * Factory to produce.
 *
 * Future extensions might include:
 *  - Larger spot size validation
 *  - Premium pricing for four-wheelers
 *  - EV charging spot reservation
 *
 * [OOP — Liskov Substitution Principle (LSP)]
 *   Both TwoWheelerSpotManager and FourWheelerSpotManager can be used
 *   anywhere a ParkingSpotManager is expected — no surprises, no
 *   behavioral violations. This is LSP in practice: subtypes must be
 *   substitutable for their base type without breaking the program.
 */
public class FourWheelerSpotManager extends ParkingSpotManager{
    public FourWheelerSpotManager(List<ParkingSpot> spots, ParkingSpotLookupStrategy strategy){
        super(strategy, spots);
    }
}
