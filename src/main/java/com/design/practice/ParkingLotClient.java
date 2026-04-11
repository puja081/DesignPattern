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

/**
 * =====================================================================
 * PARKING LOT — LOW-LEVEL DESIGN (LLD) CLIENT
 * =====================================================================
 *
 * This class wires together all the components and demonstrates the
 * complete vehicle entry → parking → payment → exit flow.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                    DESIGN PATTERNS USED                            │
 * ├──────────────┬──────────────────────────────────────────────────────┤
 * │ Pattern      │ Where                                               │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ Strategy     │ 1. ParkingSpotLookupStrategy → spot selection algo  │
 * │              │ 2. PricingStrategy           → cost calculation     │
 * │              │ 3. Payment                   → payment method       │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ Factory      │ ParkingSpotManagerFactory → creates the right       │
 * │              │ manager subclass based on VehicleType               │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ Singleton    │ ParkingLot.getInstance() → one lot instance         │
 * │              │ (double-checked locking + volatile)                 │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ Observer     │ ParkingEventListener → DisplayBoard, AdminNotif     │
 * │              │ ParkingBuilding is the Subject                      │
 * └──────────────┴──────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                    OOP / JAVA CONCEPTS USED                        │
 * ├──────────────────────┬──────────────────────────────────────────────┤
 * │ Encapsulation        │ Private fields + getters in all entities    │
 * │ Abstraction          │ Interfaces for Strategy, Payment, Observer  │
 * │ Inheritance          │ ParkingSpotManager → Two/FourWheeler mgrs   │
 * │ Polymorphism         │ payment.pay(), strategy.selectSpot() etc.   │
 * │ Composition          │ ParkingLot has-a Building, Gates            │
 * │ Immutability         │ Ticket (all final fields)                   │
 * │ Enums                │ VehicleType, ParkingEvent                   │
 * │ Thread Safety        │ ReentrantLock, volatile, DCL Singleton      │
 * │ Defensive Copy       │ new ArrayList<>() in ParkingBuilding ctor   │
 * │ Method References    │ ParkingSpot::isSpotFree in stream           │
 * │ Dependency Injection │ Strategies injected via constructors        │
 * │ SOLID Principles     │ SRP, OCP, LSP, ISP, DIP — all demonstrated │
 * └──────────────────────┴──────────────────────────────────────────────┘
 *
 * FLOW:
 *   1. Create a spot lookup strategy (Strategy #1)
 *   2. Create a factory that uses this strategy to produce managers
 *   3. Build parking levels, each with managers per vehicle type
 *   4. Create a pricing strategy (Strategy #2) wrapped in CostComputation
 *   5. Create the ParkingBuilding with levels and observer listeners
 *   6. Obtain the Singleton ParkingLot
 *   7. Vehicles arrive → EntranceGate → Building.allocate() → Ticket
 *   8. Vehicles exit  → ExitGate → CostComputation → Payment (Strategy #3) → release spot
 */
public class ParkingLotClient {
    public static void main(String[] args) {

        // ── STEP 1: Strategy #1 — Spot Lookup ──────────────────────────
        // Choosing "first available" algorithm. To switch to nearest-to-entrance,
        // just swap: new NearestToEntranceLookupStrategy()
        ParkingSpotLookupStrategy strategy = new RandomLookupStrategy();

        // ── STEP 2: Factory — creates the right ParkingSpotManager subclass ──
        // The factory receives the lookup strategy and injects it into every
        // manager it creates. Client code never calls "new TwoWheelerSpotManager" directly.
        ParkingSpotManagerFactory managerFactory = new ParkingSpotManagerFactory(strategy);

        // ── STEP 3: Build Parking Levels ────────────────────────────────
        // Each level has a Map<VehicleType, ParkingSpotManager>.
        // The map-based dispatch replaces if/else chains for vehicle routing.
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

        // ── STEP 4: Strategy #2 — Pricing ──────────────────────────────
        // Fixed pricing for now. To switch to hourly: new HourlyPricingStrategy()
        // CostComputation is the Strategy "Context" — it delegates to the strategy.
        CostComputation costComputation = new CostComputation(new FixedPricingStartegy());

        // ── STEP 5: Observer — ParkingBuilding as Subject ───────────────
        // Listeners are registered both via constructor and via addListener().
        // ParkingBuilding defensively copies the list to make it mutable.
        ParkingBuilding parkingBuilding = new ParkingBuilding(
                List.of(level1, level2),
                List.of(new DisplayBoardListener(), new AdminNotificationListener())
        );

        parkingBuilding.addListener(new DisplayBoardListener());
        parkingBuilding.addListener(new AdminNotificationListener());

        // ── STEP 6: Singleton — get the one ParkingLot instance ─────────
        // Uses double-checked locking (see ParkingLot class for detailed explanation).
        // Subsequent calls with different args would still return the SAME instance.
        ParkingLot parkingLot = ParkingLot.getInstance(
                parkingBuilding,
                new EntranceGate(),
                new ExitGate(costComputation)
        );

        // ── STEP 7: Vehicle Entry Flow ──────────────────────────────────
        // vehicleArrives() → EntranceGate.enter() → ParkingBuilding.allocate()
        //   → ParkingLevel.park() → ParkingSpotManager.park() [thread-safe]
        //     → ParkingSpotLookupStrategy.selectSpot() [Strategy in action]
        //       → ParkingSpot.occupySpot() → returns Ticket
        Vehicle bike = new Vehicle("BIKE-101", VehicleType.TWO_WHEELER);
        Vehicle car = new Vehicle("CAR-201", VehicleType.FOUR_WHEELER);
        Ticket t1 = parkingLot.vehicleArrives(bike);
        Ticket t2 = parkingLot.vehicleArrives(car);

        // ── STEP 8: Vehicle Exit Flow ───────────────────────────────────
        // vehicleExits() → ExitGate.completeExit()
        //   → CostComputation.compute() → PricingStrategy.calculate() [Strategy]
        //   → Payment.pay() [Strategy — polymorphism: CashPayment vs UpiPayment]
        //   → ParkingBuilding.release() → ParkingSpotManager.unPark() [thread-safe]
        parkingLot.vehicleExits(t1, new CashPayment());
        parkingLot.vehicleExits(t2, new UpiPayment());
    }
}
