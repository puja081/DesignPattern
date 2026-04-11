package com.design.practice.parkinglot;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.Entity.Ticket;
import com.design.practice.Entity.Vehicle;
import com.design.practice.ParkingLotClient;
import com.design.practice.payment.Payment;

public class ParkingLot {
    private static volatile ParkingLot instance;
    private final ParkingBuilding building;
    private final EntranceGate entranceGate;
    private final ExitGate exitGate;

    public ParkingLot(ParkingBuilding building, EntranceGate entranceGate, ExitGate exitGate) {
        this.building = building;
        this.entranceGate = entranceGate;
        this.exitGate = exitGate;
    }

    public static ParkingLot getInstance(ParkingBuilding building, EntranceGate entranceGate, ExitGate exitGate){
        //private constructor -- nobody else can instantiate
        // volatile on the instance field -- ensures visibility across threads
        // Double-checked locking -- the synchronized block is only entered when instance is null, so after initialization there's zero locking overhead
        // The client code changes from new ParkingLot(...) to ParkingLot.getInstance(...)
        if(instance == null) {
            synchronized (ParkingLot.class) {
                if(instance == null){
                    instance = new ParkingLot(building, entranceGate,exitGate);
                }
            }
        }
        return instance;
    }


    public Ticket vehicleArrives(Vehicle vehicle) {
        return entranceGate.enter(building, vehicle);
    }

    public void vehicleExits(Ticket ticket, Payment payment) {
        exitGate.completeExit(building, ticket, payment);
    }
}
