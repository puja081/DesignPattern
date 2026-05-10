package com.design.practice.parking_lot.factory;

import com.design.practice.parking_lot.Entity.ParkingSpot;
import com.design.practice.parking_lot.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.parking_lot.enums.VehicleType;
import com.design.practice.parking_lot.parkingspotmanager.FourWheelerSpotManager;
import com.design.practice.parking_lot.parkingspotmanager.ParkingSpotManager;
import com.design.practice.parking_lot.parkingspotmanager.TwoWheelerSpotManager;

import java.util.List;

/**
 * =====================================================================
 * DESIGN PATTERN: Factory (Creational)
 * =====================================================================
 *
 * Intent: Encapsulate object creation logic so the caller doesn't need
 *         to know which concrete class to instantiate.
 *
 * Structure in this project:
 *   - Factory         → ParkingSpotManagerFactory (this class)
 *   - Product (base)  → ParkingSpotManager
 *   - Concrete products → TwoWheelerSpotManager, FourWheelerSpotManager
 *
 * Why Factory here?
 *   The client code (ParkingLotClient) shouldn't decide "if TWO_WHEELER
 *   then new TwoWheelerSpotManager(...)". That couples the client to
 *   every concrete manager class. The Factory centralizes that decision.
 *
 * Key interview points:
 *  - This is a Simple Factory (also called Static Factory in some books),
 *    not the full Abstract Factory or Factory Method pattern. It's the most
 *    common variant in real codebases.
 *  - The factory itself receives the ParkingSpotLookupStrategy via
 *    constructor injection — combining Factory + Strategy patterns.
 *    This is common in practice: factories often wire dependencies into
 *    the objects they create.
 *  - Java 14+ enhanced switch (arrow syntax) used here — cleaner than
 *    traditional switch with break statements, and the compiler warns
 *    about missing cases.
 *  - To add a new vehicle type, you add a case here and a new manager class.
 *    The rest of the system remains unchanged.
 */
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
