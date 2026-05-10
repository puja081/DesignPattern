package com.design.practice.parking_lot.parkinglot;

import com.design.practice.parking_lot.Entity.Ticket;
import com.design.practice.parking_lot.Entity.Vehicle;

/**
 * [OOP — Single Responsibility Principle]
 *
 * EntranceGate has one job: handle vehicle entry.
 * It delegates to ParkingBuilding.allocate() for the actual spot assignment.
 *
 * Key interview points:
 *  - Separating EntranceGate and ExitGate (instead of one "Gate" class with
 *    an entry/exit mode flag) follows SRP — each class has one reason to change.
 *  - EntranceGate could be extended to handle: ticket printing, barrier control,
 *    vehicle recognition (license plate scanning), etc. — all entry concerns.
 *  - It receives ParkingBuilding as a parameter (not stored as a field) because
 *    in theory a gate could serve multiple buildings. This keeps it flexible.
 */
public class EntranceGate {
    public Ticket enter(ParkingBuilding building, Vehicle vehicle) {
        return building.allocate(vehicle);

    }
}
