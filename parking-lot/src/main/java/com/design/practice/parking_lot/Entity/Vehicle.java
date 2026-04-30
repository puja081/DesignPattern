package com.design.practice.parking_lot.Entity;

import com.design.practice.parking_lot.enums.VehicleType;

/**
 * [OOP — Encapsulation]
 *
 * Vehicle is a simple POJO / domain model that bundles related state
 * (vehicleNumber + vehicleType) behind a well-defined public API.
 *
 * Key interview points:
 *  - Fields are package-private here, but ideally should be 'private' to fully
 *    enforce encapsulation. Only expose state through getters (no setters here
 *    makes this effectively immutable after construction — a good practice).
 *  - Immutable objects are inherently thread-safe; no synchronization needed.
 *  - Uses composition with VehicleType enum instead of subclassing
 *    (e.g., Bike extends Vehicle). This avoids class explosion and lets the
 *    type be a runtime value — prefer composition over inheritance when the
 *    only difference is a "type" field.
 */
public class Vehicle {
    String vehicleNumber;
    VehicleType vehicleType;

    public Vehicle(String vehicleNumber, VehicleType vehicleType) {
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
    }
    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}
