package com.design.practice.parking_lot.Entity;

import com.design.practice.parking_lot.parkinglot.ParkingLevel;

import java.time.LocalDateTime;

/**
 * [OOP — Immutability + Composition]
 *
 * Ticket is an immutable value object — all fields are 'final' and set only
 * in the constructor. Once created, a Ticket can never change, which makes it
 * safe to pass around without defensive copies.
 *
 * Key interview points:
 *  - 'final' on fields guarantees they are assigned exactly once (at construction).
 *    The compiler enforces this — you get a compile error if you miss an assignment.
 *  - LocalDateTime.now() captures the entry timestamp automatically — encapsulating
 *    the "when" inside the constructor keeps the caller's code simpler.
 *  - Ticket composes Vehicle, ParkingLevel, and ParkingSpot rather than
 *    duplicating their data — this is the Composition principle ("has-a"
 *    relationships). If Vehicle changes, Ticket doesn't need to change.
 *  - In a real system, Ticket would also have a unique ticketId (UUID) for
 *    database persistence and lookup.
 */
public class Ticket {
    private final Vehicle vehicle;
    private final LocalDateTime entryTime;
    private final ParkingLevel parkingLevel;
    private final ParkingSpot parkingSpot;

    public Ticket(Vehicle vehicle,
                  ParkingLevel level,
                  ParkingSpot spot) {
        this.vehicle = vehicle;
        this.parkingLevel = level;
        this.parkingSpot = spot;
        this.entryTime = LocalDateTime.now();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public ParkingLevel getParkingLevel() {
        return parkingLevel;
    }

    public ParkingSpot getParkingSpot() {
        return parkingSpot;
    }
}
