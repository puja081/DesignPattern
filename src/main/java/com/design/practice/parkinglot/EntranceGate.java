package com.design.practice.parkinglot;

import com.design.practice.Entity.Ticket;
import com.design.practice.Entity.Vehicle;

public class EntranceGate {
    public Ticket enter(ParkingBuilding building, Vehicle vehicle) {
        return building.allocate(vehicle);

    }
}
