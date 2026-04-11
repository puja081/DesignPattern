package com.design.practice;

import com.design.practice.Entity.ParkingSpot;
import com.design.practice.Entity.Ticket;
import com.design.practice.Entity.Vehicle;
import com.design.practice.Events.AdminNotificationListener;
import com.design.practice.Events.DisplayBoardListener;
import com.design.practice.Events.ParkingEventListener;
import com.design.practice.LookUpStrategy.ParkingSpotLookupStrategy;
import com.design.practice.LookUpStrategy.RandomLookupStrategy;
import com.design.practice.enums.VehicleType;
import com.design.practice.factory.ParkingSpotManagerFactory;
import com.design.practice.parkinglot.*;
import com.design.practice.parkingspotmanager.ParkingSpotManager;
import com.design.practice.payment.CashPayment;
import com.design.practice.payment.UpiPayment;
import com.design.practice.pricing.CostComputation;
import com.design.practice.pricing.FixedPricingStartegy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingLotClient {
    public static void main(String[] args) {
        ParkingSpotLookupStrategy strategy = new RandomLookupStrategy();

        ParkingSpotManagerFactory managerFactory = new ParkingSpotManagerFactory(strategy);
        Map<VehicleType, ParkingSpotManager> levelOneManager = new HashMap<>();
        levelOneManager.put(VehicleType.TWO_WHEELER, managerFactory.create(VehicleType.TWO_WHEELER,
                List.of(new ParkingSpot("L1-S1"), new ParkingSpot("L1-S2"))));
        levelOneManager.put(VehicleType.FOUR_WHEELER, managerFactory.create(VehicleType.FOUR_WHEELER,
                List.of(new ParkingSpot("L1-S3"), new ParkingSpot("L1-s4"))));
        ParkingLevel level1 = new ParkingLevel(
                1, levelOneManager
        );

        Map<VehicleType, ParkingSpotManager> levelTwoManager = new HashMap<>();
        levelTwoManager.put(VehicleType.TWO_WHEELER, managerFactory.create(VehicleType.TWO_WHEELER,
                List.of(new ParkingSpot("L2-S1"), new ParkingSpot("L2-S2"))));
        levelTwoManager.put(VehicleType.FOUR_WHEELER, managerFactory.create(VehicleType.FOUR_WHEELER,
                List.of(new ParkingSpot("L2-S3"), new ParkingSpot("L2-S4"))));

        ParkingLevel level2 = new ParkingLevel(
                2, levelTwoManager
        );

        // --- Strategy: Pricing ---
        CostComputation costComputation = new CostComputation(new FixedPricingStartegy());


        ParkingBuilding parkingBuilding = new ParkingBuilding(
                List.of(level1, level2),
                List.of(new DisplayBoardListener(), new AdminNotificationListener())
        );

        // --- Observer: Register listeners after building is created ---
        parkingBuilding.addListener(new DisplayBoardListener());
        parkingBuilding.addListener(new AdminNotificationListener());

        // --- Singleton: Get the single ParkingLot instance ---
        ParkingLot parkingLot = ParkingLot.getInstance(
                parkingBuilding,
                new EntranceGate(),
                new ExitGate(costComputation)
        );
        Vehicle bike = new Vehicle("BIKE-101", VehicleType.TWO_WHEELER);
        Vehicle car = new Vehicle("CAR-201", VehicleType.FOUR_WHEELER);
        Ticket t1 = parkingLot.vehicleArrives(bike);
        Ticket t2 = parkingLot.vehicleArrives(car);
        // --- Strategy: Payment ---
        parkingLot.vehicleExits(t1, new CashPayment());
        parkingLot.vehicleExits(t2, new UpiPayment());
    }
}