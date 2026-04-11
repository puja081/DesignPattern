package com.design.practice.Entity;

import com.design.practice.parkinglot.ParkingLevel;

import java.time.LocalDateTime;

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
